package com.dfs.upload.config;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.upload.infrastructure.adapter.out.blob.BlobNodeClients;
import io.grpc.Channel;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client half of the byte path: one gRPC channel per configured blob node.
 * Only blob nodes are reached over gRPC; the metadata service stays on REST, so
 * the orchestrator uses the same public API as any other client and its
 * authorization is exercised on the common path (ADR 10).
 *
 * <p>Each node gets a named channel, {@code blob-node-<id>}, built by the
 * starter's channel factory, so it carries the same settings as any other
 * channel: everything under {@code grpc.client.GLOBAL} applies, and a node can
 * be tuned individually under {@code grpc.client.blob-node-<id>}. The one thing
 * not read from there is the address: that comes from the node's entry in
 * {@code dfs.upload.blob-nodes}, so a node's id and address are declared
 * together and cannot drift apart.
 *
 * <p>Note what is absent, as with the REST clients: no credential. Authorization
 * is attached per call from the caller's own token — see
 * {@link com.dfs.upload.domain.model.CallerToken}.
 */
@Configuration
public class UploadGrpcClientConfiguration {

    /** Prefix of the {@code grpc.client.*} block each node's channel is configured from. */
    public static final String BLOB_NODE_CHANNEL_PREFIX = "blob-node-";

    @Bean
    BlobNodeClients blobNodeClients(GrpcChannelFactory channels,
                                    GrpcChannelsProperties channelProperties,
                                    UploadProperties properties) {
        Map<String, BlobNodeClients.Stubs> stubs = new LinkedHashMap<>();
        for (UploadProperties.BlobNode node : UploadInfrastructureConfiguration.configuredNodes(properties)) {
            if (node.target() == null || node.target().isBlank()) {
                throw new IllegalStateException("Blob node '%s' has no target address".formatted(node.id()));
            }
            String channelName = BLOB_NODE_CHANNEL_PREFIX + node.id();
            // Set before the channel is created: the factory reads the address
            // when it first builds a channel for this name, and caches it.
            channelProperties.getChannel(channelName).setAddress(node.target());
            Channel channel = channels.createChannel(channelName);
            stubs.put(node.id(), new BlobNodeClients.Stubs(
                    BlobNodeServiceGrpc.newStub(channel),
                    BlobNodeServiceGrpc.newBlockingStub(channel)));
        }
        return new BlobNodeClients(stubs);
    }
}
