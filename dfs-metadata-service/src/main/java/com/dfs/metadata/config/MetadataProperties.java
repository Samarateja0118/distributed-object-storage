package com.dfs.metadata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dfs.metadata")
public record MetadataProperties(
        Duration cacheTtl,
        int defaultReplicationFactor,
        int defaultChunkSizeMb
) {
}
