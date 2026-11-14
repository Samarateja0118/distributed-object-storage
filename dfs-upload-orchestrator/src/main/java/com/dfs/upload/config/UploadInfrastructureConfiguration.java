package com.dfs.upload.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dfs.common.infrastructure.config.KafkaConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;
import com.dfs.common.infrastructure.config.RedisConfiguration;
import com.dfs.upload.application.service.ChunkingPolicy;
import com.dfs.upload.domain.placement.RendezvousPlacement;

import java.util.List;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
@Import({RedisConfiguration.class, KafkaConfiguration.class, OpenApiConfiguration.class})
public class UploadInfrastructureConfiguration {

    /**
     * The width of {@code object_chunks.node_id} in the metadata service. An id
     * longer than that would be accepted here, written to a node, and then
     * rejected when metadata is registered, so it is refused at startup instead.
     */
    static final int MAX_NODE_ID_LENGTH = 64;

    /**
     * Hands the transfer service the two values it needs rather than the whole
     * configuration record, so the application layer stays unaware of how this
     * process happens to be configured.
     */
    @Bean
    ChunkingPolicy chunkingPolicy(UploadProperties properties) {
        return new ChunkingPolicy(
                (int) properties.chunkSize().toBytes(),
                properties.maxObjectSize().toBytes());
    }

    /**
     * Built once at startup, so a bad node set — empty, a duplicate id, an id too
     * long to record — stops the process before it writes a chunk anywhere.
     */
    @Bean
    RendezvousPlacement chunkPlacement(UploadProperties properties) {
        List<String> nodeIds = configuredNodes(properties).stream()
                .map(UploadProperties.BlobNode::id)
                .toList();
        for (String id : nodeIds) {
            if (id != null && id.length() > MAX_NODE_ID_LENGTH) {
                throw new IllegalStateException("Blob node id '%s' is longer than the %d characters metadata records"
                        .formatted(id, MAX_NODE_ID_LENGTH));
            }
        }
        return new RendezvousPlacement(nodeIds);
    }

    static List<UploadProperties.BlobNode> configuredNodes(UploadProperties properties) {
        List<UploadProperties.BlobNode> nodes = properties.blobNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("No blob nodes configured: set dfs.upload.blob-nodes");
        }
        return nodes;
    }
}
