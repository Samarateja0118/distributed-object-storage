package com.dfs.blob.api.grpc;

import com.dfs.blob.application.port.in.BlobStorageUseCase;
import com.dfs.blob.domain.exception.BlobNotFoundException;
import com.dfs.blob.domain.exception.BlobValidationException;
import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;
import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.proto.blob.v1.ChunkExistsRequest;
import com.dfs.proto.blob.v1.ChunkExistsResponse;
import com.dfs.proto.blob.v1.GetChunkRequest;
import com.dfs.proto.blob.v1.GetChunkResponse;
import com.dfs.proto.blob.v1.PutChunkHeader;
import com.dfs.proto.blob.v1.PutChunkRequest;
import com.dfs.proto.blob.v1.PutChunkResponse;
import com.dfs.proto.common.v1.ChunkId;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The gRPC face of the blob node. Translates frames to a call on
 * {@link BlobStorageUseCase} and back, and holds no storage logic of its own —
 * this is the same adapter role {@code BlobController} plays for REST, so the two
 * transports cannot diverge in behavior.
 *
 * <p>Authentication is not handled here: a global interceptor validates the token
 * with the shared decoder before any of these methods run.
 */
@GrpcService
public class BlobNodeGrpcService extends BlobNodeServiceGrpc.BlobNodeServiceImplBase {

    private static final int FRAME_BYTES = 64 * 1024;

    /**
     * Bounds how far the receiving side may run ahead of the thread writing to
     * disk. Small enough to stay a buffer rather than a copy of the chunk, large
     * enough that a writer is not woken per frame.
     */
    private static final int PIPE_BUFFER_BYTES = 256 * 1024;

    private final BlobStorageUseCase blobs;

    public BlobNodeGrpcService(BlobStorageUseCase blobs) {
        this.blobs = blobs;
    }

    @Override
    public StreamObserver<PutChunkRequest> putChunk(StreamObserver<PutChunkResponse> responses) {
        return new PutChunkFrames(responses);
    }

    @Override
    public void getChunk(GetChunkRequest request, StreamObserver<GetChunkResponse> responses) {
        try {
            BlobDigest digest = new BlobDigest(request.getContentHash());
            try (InputStream content = blobs.open(digest)) {
                byte[] buffer = new byte[FRAME_BYTES];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    responses.onNext(GetChunkResponse.newBuilder()
                            .setData(ByteString.copyFrom(buffer, 0, read))
                            .build());
                }
            }
            responses.onCompleted();
        } catch (Exception e) {
            responses.onError(asStatus(e));
        }
    }

    @Override
    public void chunkExists(ChunkExistsRequest request, StreamObserver<ChunkExistsResponse> responses) {
        try {
            BlobDigest digest = new BlobDigest(request.getContentHash());
            ChunkExistsResponse response = blobs.stat(digest)
                    .map(stored -> ChunkExistsResponse.newBuilder()
                            .setExists(true)
                            .setSizeBytes(stored.sizeBytes())
                            .build())
                    .orElseGet(() -> ChunkExistsResponse.newBuilder().setExists(false).build());
            responses.onNext(response);
            responses.onCompleted();
        } catch (Exception e) {
            responses.onError(asStatus(e));
        }
    }

    /**
     * Bridges the incoming frames onto the {@link InputStream} the storage port
     * reads from. gRPC delivers bytes by push and the port consumes them by pull,
     * so one side has to wait on the other: a pipe with a virtual thread doing the
     * write is what lets the node hash and store as frames arrive instead of
     * buffering the whole chunk to hand over at the end.
     */
    private final class PutChunkFrames implements StreamObserver<PutChunkRequest> {

        private final StreamObserver<PutChunkResponse> responses;

        private BlobDigest digest;
        private long declaredSize;
        private long receivedSize;
        private PipedOutputStream sink;
        private CompletableFuture<StoredBlob> stored;
        private boolean finished;

        private PutChunkFrames(StreamObserver<PutChunkResponse> responses) {
            this.responses = responses;
        }

        @Override
        public void onNext(PutChunkRequest frame) {
            if (finished) {
                return;
            }
            try {
                switch (frame.getPayloadCase()) {
                    case HEADER -> begin(frame.getHeader());
                    case DATA -> accept(frame.getData());
                    case PAYLOAD_NOT_SET -> throw Status.INVALID_ARGUMENT
                            .withDescription("Frame carried neither a header nor data")
                            .asRuntimeException();
                }
            } catch (Exception e) {
                abort(e);
            }
        }

        private void begin(PutChunkHeader header) throws IOException {
            if (sink != null) {
                throw Status.INVALID_ARGUMENT.withDescription("Header sent more than once").asRuntimeException();
            }
            digest = new BlobDigest(header.getContentHash());
            declaredSize = header.getSizeBytes();

            PipedInputStream source = new PipedInputStream(PIPE_BUFFER_BYTES);
            sink = new PipedOutputStream(source);
            stored = new CompletableFuture<>();

            // Virtual thread: this one blocks on disk for the whole transfer, and
            // the node should not be limited to a pool's worth of concurrent writes.
            Thread.ofVirtual().name("put-chunk-" + digest.value()).start(() -> {
                try (InputStream content = source) {
                    StoredBlob result = blobs.store(digest, content);
                    // A chunk the node already holds is stored without reading a
                    // byte, and closing the pipe under a caller that is still
                    // sending fails its next write. Draining to the end keeps the
                    // writer unblocked whatever the store did with the stream, and
                    // keeps the declared-size check honest by letting every frame
                    // the caller promised actually arrive.
                    content.transferTo(OutputStream.nullOutputStream());
                    stored.complete(result);
                } catch (Throwable t) {
                    stored.completeExceptionally(t);
                }
            });
        }

        private void accept(ByteString data) throws IOException {
            if (sink == null) {
                throw Status.INVALID_ARGUMENT.withDescription("Data frame arrived before the header")
                        .asRuntimeException();
            }
            receivedSize += data.size();
            data.writeTo(sink);
        }

        @Override
        public void onCompleted() {
            if (finished) {
                return;
            }
            try {
                if (sink == null) {
                    throw Status.INVALID_ARGUMENT.withDescription("Stream carried no header").asRuntimeException();
                }
                sink.close();

                // Checked before the digest, because "you sent 900 of the 1000 bytes
                // you promised" localizes a truncated upload better than a hash that
                // does not match for reasons unstated.
                if (receivedSize != declaredSize) {
                    throw Status.INVALID_ARGUMENT
                            .withDescription("Header declared %d bytes but %d arrived"
                                    .formatted(declaredSize, receivedSize))
                            .asRuntimeException();
                }

                StoredBlob blob = stored.join();
                finished = true;
                responses.onNext(PutChunkResponse.newBuilder()
                        .setChunkId(ChunkId.newBuilder().setValue(blob.digest().value()).build())
                        .setContentHash(blob.digest().value())
                        .setSizeBytes(blob.sizeBytes())
                        .build());
                responses.onCompleted();
            } catch (Exception e) {
                abort(e);
            }
        }

        @Override
        public void onError(Throwable t) {
            // The caller is gone, so there is nobody to answer. Closing the pipe is
            // what stops the storage thread from blocking forever on a stream that
            // will never receive another byte.
            finished = true;
            closeQuietly();
        }

        private void abort(Throwable failure) {
            if (finished) {
                return;
            }
            finished = true;
            closeQuietly();
            // A write failing because the reader died is a symptom; the storage
            // failure that killed it is the cause worth reporting.
            responses.onError(asStatus(storageFailureOr(failure)));
        }

        private Throwable storageFailureOr(Throwable fallback) {
            if (stored != null && stored.isCompletedExceptionally()) {
                try {
                    stored.join();
                } catch (CompletionException e) {
                    return e.getCause() != null ? e.getCause() : fallback;
                } catch (Exception e) {
                    return e;
                }
            }
            return fallback;
        }

        private void closeQuietly() {
            if (sink == null) {
                return;
            }
            try {
                sink.close();
            } catch (IOException ignored) {
                // Already failing; the close error adds nothing.
            }
        }
    }

    private static StatusRuntimeException asStatus(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof StatusRuntimeException alreadyMapped) {
            return alreadyMapped;
        }
        if (cause instanceof BlobValidationException) {
            // Covers a malformed digest and a body that does not hash to the name it
            // was filed under; both are the caller's error.
            return Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).asRuntimeException();
        }
        if (cause instanceof BlobNotFoundException) {
            return Status.NOT_FOUND.withDescription(cause.getMessage()).asRuntimeException();
        }
        // Anything else is ours, and its message is not the caller's business.
        return Status.INTERNAL.withDescription("Blob operation failed").asRuntimeException();
    }
}
