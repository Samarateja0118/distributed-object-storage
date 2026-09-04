package com.dfs.upload.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dfs.common.infrastructure.config.KafkaConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;
import com.dfs.common.infrastructure.config.RedisConfiguration;
import com.dfs.upload.application.service.ChunkPlacement;
import com.dfs.upload.application.service.ChunkingPolicy;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
@Import({RedisConfiguration.class, KafkaConfiguration.class, OpenApiConfiguration.class})
public class UploadInfrastructureConfiguration {

    /**
     * Hands the transfer service the three values it needs rather than the whole
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
     * The node set placement ranks over (ADR 11). Built from the same list the
     * gRPC channels are, so a node cannot be reachable but unplaceable, or
     * ranked but unreachable.
     */
    @Bean
    ChunkPlacement chunkPlacement(UploadProperties properties) {
        return new ChunkPlacement(properties.blobNodes());
    }
}
