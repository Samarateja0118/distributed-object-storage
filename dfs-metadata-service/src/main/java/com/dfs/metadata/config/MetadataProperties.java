package com.dfs.metadata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "dfs.metadata")
public record MetadataProperties(
        Duration cacheTtl,
        int defaultReplicationFactor,
        int defaultChunkSizeMb,
        Multipart multipart
) {

    /**
     * Multipart upload sessions (ADR 12). The metadata service owns them, so
     * their lifetime is configured here and nowhere else.
     *
     * @param sessionTtl      how long an upload may stay open
     * @param minPartSize     smallest part allowed unless it is the last
     * @param sweepInterval   delay between expiry sweeps
     * @param sweepBatchSize  sessions deleted per sweep transaction
     */
    public record Multipart(Duration sessionTtl, DataSize minPartSize, Duration sweepInterval, int sweepBatchSize) {
    }
}
