package com.dfs.upload.config;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.upload.infrastructure.adapter.out.blob.BlobNodeStubs;
import io.grpc.Channel;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client half of the byte path. Only blob nodes are reached over gRPC; the
 * metadata service stays on REST, so the orchestrator uses the same public API
 * as any other client and its authorization is exercised on the common path
 * (ADR 10).
 *
 * <p>Stubs as beans rather than {@code @GrpcClient}-annotated fields: field
 * injection hides a dependency and makes the adapter untestable without a
 * container, and this service uses constructor injection everywhere else.
 *
 * <p>One channel per configured node, named by the node's id. That id is both
 * the key placement ranks over and the name of the {@code grpc.client.<id>}
 * block holding its address, so a node is configured in one place and referred
 * to by one name everywhere.
 *
 * <p>Note what is absent, as with the REST clients: no credential. Authorization
 * is attached per call from the caller's own token — see
 * {@link com.dfs.upload.domain.model.CallerToken}.
 */
@Configuration
public class UploadGrpcClientConfiguration {

    @Bean
    BlobNodeStubs blobNodeStubs(GrpcChannelFactory channels, UploadProperties properties) {
        Map<String, BlobNodeStubs.Stubs> stubs = new LinkedHashMap<>();
        for (String nodeId : properties.blobNodes()) {
            Channel channel = channels.createChannel(nodeId);
            stubs.put(nodeId, new BlobNodeStubs.Stubs(
                    BlobNodeServiceGrpc.newStub(channel),
                    BlobNodeServiceGrpc.newBlockingStub(channel)));
        }
        return new BlobNodeStubs(stubs);
    }
}
