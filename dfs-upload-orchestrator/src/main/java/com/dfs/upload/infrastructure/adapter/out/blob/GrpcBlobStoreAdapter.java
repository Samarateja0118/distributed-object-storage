package com.dfs.upload.infrastructure.adapter.out.blob;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.proto.blob.v1.GetChunkRequest;
import com.dfs.proto.blob.v1.GetChunkResponse;
import com.dfs.proto.blob.v1.PutChunkHeader;
import com.dfs.proto.blob.v1.PutChunkRequest;
import com.dfs.proto.blob.v1.PutChunkResponse;
import com.dfs.upload.application.port.out.BlobStorePort;
import com.dfs.upload.config.UploadProperties;
import com.dfs.upload.domain.exception.DownstreamServiceException;
import com.dfs.upload.domain.exception.DownstreamUnavailableException;
import com.dfs.upload.domain.exception.ObjectNotFoundException;
import com.dfs.upload.domain.model.CallerToken;
import com.google.protobuf.ByteString;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the blob nodes over gRPC, each through its own channel.
 *
 * <p>The transport is streaming in both directions, so neither side holds a whole
 * chunk in memory on the wire (ADR 10).
 *
 * <p>This adapter does not choose nodes. It is told which node to call, by the
 * placement ranking on a write and by the recorded node on a read (ADR 11), and
 * reports a node that did not answer as {@link DownstreamUnavailableException} so
 * the caller can decide whether to try another.
 *
 * <p>No credential of its own: every call carries the caller's token as
 * {@code Authorization} metadata, which is the same token the resource server
 * validated (ADR 7).
 */
@Component
public class GrpcBlobStoreAdapter implements BlobStorePort {

    static final String SERVICE_NAME = "blob node";

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Matches the node's own frame size. Well under gRPC's 4 MiB message limit,
     * and large enough that framing overhead stays negligible against the copy.
     */
    private static final int FRAME_BYTES = 64 * 1024;

    private final BlobNodeClients nodes;
    private final long deadlineMillis;

    public GrpcBlobStoreAdapter(BlobNodeClients nodes, UploadProperties properties) {
        this.nodes = nodes;
        this.deadlineMillis = properties.downstreamTimeout().toMillis();
    }

    /**
     * A header naming the digest and size, then the content in frames.
     *
     * <p>The call is answered once, at the end, so waiting on the response is
     * what makes this synchronous from the service's point of view. The deadline
     * rather than a timeout on the wait is what bounds it: gRPC guarantees the
     * call is closed when the deadline passes, so there is no interleaving in
     * which the response never arrives.
     */
    @Override
    public void store(String nodeId, String digest, byte[] content, CallerToken token) {
        BlobNodeServiceGrpc.BlobNodeServiceStub asyncStub = nodes.forNode(nodeId).async();
        CompletableFuture<PutChunkResponse> response = new CompletableFuture<>();
        StreamObserver<PutChunkRequest> frames = asyncStub
                .withInterceptors(bearer(token))
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .putChunk(completing(response));

        try {
            frames.onNext(PutChunkRequest.newBuilder()
                    .setHeader(PutChunkHeader.newBuilder()
                            .setContentHash(digest)
                            .setSizeBytes(content.length))
                    .build());
            for (int offset = 0; offset < content.length; offset += FRAME_BYTES) {
                int length = Math.min(FRAME_BYTES, content.length - offset);
                frames.onNext(PutChunkRequest.newBuilder()
                        .setData(ByteString.copyFrom(content, offset, length))
                        .build());
            }
            frames.onCompleted();
        } catch (RuntimeException alreadyClosed) {
            // The node rejected the call while we were still sending. Its status
            // is on the response observer, which carries more than this does.
        }

        await(response, nodeId, digest);
    }

    @Override
    public byte[] fetch(String nodeId, String digest, CallerToken token) {
        BlobNodeServiceGrpc.BlobNodeServiceBlockingStub blockingStub = nodes.forNode(nodeId).blocking();
        try {
            Iterator<GetChunkResponse> frames = blockingStub
                    .withInterceptors(bearer(token))
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .getChunk(GetChunkRequest.newBuilder().setContentHash(digest).build());

            ByteArrayOutputStream content = new ByteArrayOutputStream();
            while (frames.hasNext()) {
                content.writeBytes(frames.next().getData().toByteArray());
            }
            return content.toByteArray();
        } catch (StatusRuntimeException e) {
            throw translate(e, nodeId, digest);
        }
    }

    private void await(CompletableFuture<PutChunkResponse> response, String nodeId, String digest) {
        try {
            response.get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof StatusRuntimeException status
                    ? translate(status, nodeId, digest)
                    : new DownstreamServiceException(SERVICE_NAME, 502, String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException(
                    "Interrupted while storing blob %s on %s".formatted(digest, nodeId), e);
        }
    }

    /**
     * Maps a gRPC status onto the failures the API layer already knows how to
     * report. The HTTP codes below are how {@code DownstreamServiceException}
     * distinguishes a rejected token from a broken dependency; they are a
     * classification, not a status the node actually sent.
     */
    private RuntimeException translate(StatusRuntimeException failure, String nodeId, String digest) {
        Status.Code code = failure.getStatus().getCode();
        return switch (code) {
            // Metadata pointed at this digest, so a miss is not a bad request —
            // it means the two halves have diverged and the object is unreadable.
            case NOT_FOUND -> new ObjectNotFoundException(
                    "Metadata references blob %s on %s but that node does not have it".formatted(digest, nodeId));
            case UNAUTHENTICATED -> new DownstreamServiceException(SERVICE_NAME, 401, describe(failure));
            case PERMISSION_DENIED -> new DownstreamServiceException(SERVICE_NAME, 403, describe(failure));
            // The node never answered: nothing was refused, so this is not a
            // failed request but an absent dependency — and the only outcome a
            // write may retry on another node.
            case UNAVAILABLE, DEADLINE_EXCEEDED -> new DownstreamUnavailableException(
                    "The %s %s did not respond: %s".formatted(SERVICE_NAME, nodeId, describe(failure)), failure);
            default -> new DownstreamServiceException(SERVICE_NAME, 502, describe(failure));
        };
    }

    private static String describe(StatusRuntimeException failure) {
        Status status = failure.getStatus();
        return status.getDescription() == null
                ? status.getCode().name()
                : "%s: %s".formatted(status.getCode().name(), status.getDescription());
    }

    private static io.grpc.ClientInterceptor bearer(CallerToken token) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, token.asBearerHeader());
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }

    private static StreamObserver<PutChunkResponse> completing(CompletableFuture<PutChunkResponse> response) {
        return new StreamObserver<>() {
            @Override
            public void onNext(PutChunkResponse value) {
                response.complete(value);
            }

            @Override
            public void onError(Throwable t) {
                response.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                // A closed stream with no response means the node broke its own
                // contract; failing here beats waiting forever.
                response.completeExceptionally(
                        Status.INTERNAL.withDescription("Stream closed without a response").asRuntimeException());
            }
        };
    }
}
