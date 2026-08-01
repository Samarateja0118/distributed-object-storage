package com.dfs.upload.config;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import io.grpc.Channel;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The client half of the byte path. Only the blob node is reached over gRPC; the
 * metadata service stays on REST, so the orchestrator uses the same public API
 * as any other client and its authorization is exercised on the common path
 * (ADR 10).
 *
 * <p>Stubs as beans rather than {@code @GrpcClient}-annotated fields: field
 * injection hides a dependency and makes the adapter untestable without a
 * container, and this service uses constructor injection everywhere else.
 *
 * <p>Two stubs for one service because the generated API splits by call style —
 * a blocking stub cannot open a client-streaming call, and the async stub is the
 * wrong shape for reading a response back.
 *
 * <p>Note what is absent, as with the REST clients: no credential. Authorization
 * is attached per call from the caller's own token — see
 * {@link com.dfs.upload.domain.model.CallerToken}.
 */
@Configuration
public class UploadGrpcClientConfiguration {

    /** Names the {@code grpc.client.*} block this channel is configured from. */
    public static final String BLOB_NODE = "blob-node";

    @Bean
    Channel blobNodeChannel(GrpcChannelFactory channels) {
        return channels.createChannel(BLOB_NODE);
    }

    @Bean
    BlobNodeServiceGrpc.BlobNodeServiceStub blobNodeStub(Channel blobNodeChannel) {
        return BlobNodeServiceGrpc.newStub(blobNodeChannel);
    }

    @Bean
    BlobNodeServiceGrpc.BlobNodeServiceBlockingStub blobNodeBlockingStub(Channel blobNodeChannel) {
        return BlobNodeServiceGrpc.newBlockingStub(blobNodeChannel);
    }
}
