package com.dfs.blob;

import com.dfs.common.hash.Sha256;
import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.proto.blob.v1.ChunkExistsRequest;
import com.dfs.proto.blob.v1.ChunkExistsResponse;
import com.dfs.proto.blob.v1.GetChunkRequest;
import com.dfs.proto.blob.v1.GetChunkResponse;
import com.dfs.proto.blob.v1.PutChunkHeader;
import com.dfs.proto.blob.v1.PutChunkRequest;
import com.dfs.proto.blob.v1.PutChunkResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the gRPC data plane over the in-process transport, against the real
 * storage adapter and the real authenticating interceptor.
 */
class BlobGrpcApiIT extends AbstractBlobNodeIT {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    /** Small enough that the multi-frame path is exercised by modest payloads. */
    private static final int FRAME_BYTES = 8 * 1024;

    /**
     * How long to hold the data frames back so the storage side is certainly
     * ahead of them. Generous on purpose: too short only lets the interleaving
     * this pins go unexercised, whereas too short a timeout would fail a correct
     * server.
     */
    private static final long STORAGE_HEAD_START = 250;

    private ManagedChannel channel;

    @BeforeEach
    void openChannel() {
        channel = InProcessChannelBuilder.forName("blob-node-test").build();
    }

    @AfterEach
    void closeChannel() throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void storesAndReadsBackAChunk() throws Exception {
        byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        PutChunkResponse response = putChunk(bearer(), digest, content.length, content);

        assertThat(response.getContentHash()).isEqualTo(digest);
        assertThat(response.getChunkId().getValue()).isEqualTo(digest);
        assertThat(response.getSizeBytes()).isEqualTo(content.length);
        assertThat(getChunk(bearer(), digest)).isEqualTo(content);
    }

    /**
     * The digest has to survive being split across frames, which is the whole
     * reason the RPC streams rather than taking one {@code bytes} field.
     */
    @Test
    void storesAChunkSpanningManyFrames() throws Exception {
        byte[] content = new byte[FRAME_BYTES * 3 + 17];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        String digest = Sha256.hexOfContent(content);

        assertThat(putChunk(bearer(), digest, content.length, content).getSizeBytes()).isEqualTo(content.length);
        assertThat(getChunk(bearer(), digest)).isEqualTo(content);
        assertThat(Files.readAllBytes(storedPathFor(digest))).isEqualTo(content);
    }

    @Test
    void storesAnEmptyChunk() throws Exception {
        byte[] content = new byte[0];
        String digest = Sha256.hexOfContent(content);

        assertThat(putChunk(bearer(), digest, 0, content).getSizeBytes()).isZero();
        assertThat(getChunk(bearer(), digest)).isEmpty();
    }

    /** Retries after a timeout depend on a repeated write being indistinguishable. */
    @Test
    void treatsARepeatedStoreAsSuccess() throws Exception {
        byte[] content = "stored twice".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        putChunk(bearer(), digest, content.length, content);
        PutChunkResponse second = putChunk(bearer(), digest, content.length, content);

        assertThat(second.getContentHash()).isEqualTo(digest);
        assertThat(getChunk(bearer(), digest)).isEqualTo(content);
    }

    /**
     * The re-store above only takes the deduplication path if the storage side
     * wins a race against frames that are still arriving. Pausing after the header
     * makes that ordering certain: the node answers for a chunk it already holds
     * without reading a byte, and the data behind the header must not fail the
     * call when the storage side is no longer consuming it.
     */
    @Test
    void treatsARepeatedStoreAsSuccessWhenDeduplicationOutrunsTheFrames() throws Exception {
        byte[] content = "already held".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);
        putChunk(bearer(), digest, content.length, content);

        PutChunkResponse second = putChunk(bearer(), digest, content.length, content, STORAGE_HEAD_START);

        assertThat(second.getContentHash()).isEqualTo(digest);
        assertThat(second.getSizeBytes()).isEqualTo(content.length);
        assertThat(getChunk(bearer(), digest)).isEqualTo(content);
    }

    @Test
    void rejectsContentThatDoesNotMatchItsDigest() {
        byte[] content = "honest bytes".getBytes(StandardCharsets.UTF_8);
        String wrongDigest = Sha256.hexOfContent("different bytes".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> putChunk(bearer(), wrongDigest, content.length, content))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.INVALID_ARGUMENT));

        assertThat(chunkExists(bearer(), wrongDigest).getExists()).isFalse();
    }

    /**
     * A truncated upload is reported against the declared size, which names the
     * problem, rather than as a digest mismatch, which only says something differs.
     */
    @Test
    void rejectsAStreamShorterThanItsHeaderDeclared() {
        byte[] content = "twenty-four bytes long!!".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        assertThatThrownBy(() -> putChunk(bearer(), digest, content.length + 10, content))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.INVALID_ARGUMENT))
                .satisfies(e -> assertThat(e).hasMessageContaining("declared"));
    }

    @Test
    void rejectsAMalformedDigest() {
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> putChunk(bearer(), "not-a-sha-256", content.length, content))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void reportsWhetherAChunkIsHeld() throws Exception {
        byte[] content = "present".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);
        String absent = Sha256.hexOfContent("absent".getBytes(StandardCharsets.UTF_8));

        putChunk(bearer(), digest, content.length, content);

        ChunkExistsResponse held = chunkExists(bearer(), digest);
        assertThat(held.getExists()).isTrue();
        assertThat(held.getSizeBytes()).isEqualTo(content.length);

        ChunkExistsResponse missing = chunkExists(bearer(), absent);
        assertThat(missing.getExists()).isFalse();
        assertThat(missing.getSizeBytes()).isZero();
    }

    @Test
    void reportsAMissingChunkAsNotFound() {
        String absent = Sha256.hexOfContent("never stored".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> getChunk(bearer(), absent))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.NOT_FOUND));
    }

    /**
     * The servlet filter chain never sees a gRPC call, so this is the only thing
     * standing between an anonymous caller and the node's disk.
     */
    @Test
    void rejectsAnUnauthenticatedWrite() {
        byte[] content = "unauthorized".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        assertThatThrownBy(() -> putChunk(null, digest, content.length, content))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void rejectsAnUnauthenticatedRead() {
        assertThatThrownBy(() -> getChunk(null, Sha256.hexOfContent("anything".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void rejectsATokenItCannotVerify() {
        assertThatThrownBy(() -> chunkExists("Bearer not-a-real-token",
                Sha256.hexOfContent("anything".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    private PutChunkResponse putChunk(String authorization, String digest, long declaredSize, byte[] content)
            throws Exception {
        return putChunk(authorization, digest, declaredSize, content, 0);
    }

    private PutChunkResponse putChunk(String authorization, String digest, long declaredSize, byte[] content,
                                      long pauseAfterHeaderMillis) throws Exception {
        CompletableFuture<PutChunkResponse> result = new CompletableFuture<>();
        StreamObserver<PutChunkRequest> frames = asyncStub(authorization).putChunk(new StreamObserver<>() {
            @Override
            public void onNext(PutChunkResponse response) {
                result.complete(response);
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                result.completeExceptionally(new IllegalStateException("Stream closed without a response"));
            }
        });

        try {
            frames.onNext(PutChunkRequest.newBuilder()
                    .setHeader(PutChunkHeader.newBuilder().setContentHash(digest).setSizeBytes(declaredSize))
                    .build());
            if (pauseAfterHeaderMillis > 0) {
                Thread.sleep(pauseAfterHeaderMillis);
            }
            for (int offset = 0; offset < content.length; offset += FRAME_BYTES) {
                int length = Math.min(FRAME_BYTES, content.length - offset);
                frames.onNext(PutChunkRequest.newBuilder()
                        .setData(ByteString.copyFrom(content, offset, length))
                        .build());
            }
            frames.onCompleted();
        } catch (RuntimeException alreadyRejected) {
            // The server may have closed the call mid-send; the response observer
            // carries the real status, so let the wait below surface it.
        }

        try {
            return result.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : new IllegalStateException(e.getCause());
        }
    }

    private byte[] getChunk(String authorization, String digest) {
        Iterator<GetChunkResponse> frames = blockingStub(authorization)
                .getChunk(GetChunkRequest.newBuilder().setContentHash(digest).build());
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        while (frames.hasNext()) {
            content.writeBytes(frames.next().getData().toByteArray());
        }
        return content.toByteArray();
    }

    private ChunkExistsResponse chunkExists(String authorization, String digest) {
        return blockingStub(authorization)
                .chunkExists(ChunkExistsRequest.newBuilder().setContentHash(digest).build());
    }

    private BlobNodeServiceGrpc.BlobNodeServiceStub asyncStub(String authorization) {
        return BlobNodeServiceGrpc.newStub(channel).withInterceptors(headers(authorization));
    }

    private BlobNodeServiceGrpc.BlobNodeServiceBlockingStub blockingStub(String authorization) {
        return BlobNodeServiceGrpc.newBlockingStub(channel).withInterceptors(headers(authorization));
    }

    private static io.grpc.ClientInterceptor headers(String authorization) {
        Metadata metadata = new Metadata();
        if (authorization != null) {
            metadata.put(AUTHORIZATION, authorization);
        }
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }

    private static Status.Code status(Throwable failure) {
        return ((StatusRuntimeException) failure).getStatus().getCode();
    }
}
