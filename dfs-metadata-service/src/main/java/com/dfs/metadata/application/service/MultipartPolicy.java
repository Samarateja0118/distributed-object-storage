package com.dfs.metadata.application.service;

import java.time.Duration;

/**
 * The limits a multipart session runs under, as plain values so the service
 * depends on the numbers rather than on how this process is configured.
 *
 * @param sessionTtl        how long an upload may stay open before it lapses
 * @param minPartSizeBytes  the smallest a part may be unless it is the last
 *                          (ADR 12: 5 MiB, as in S3)
 * @param sweepBatchSize    how many expired sessions one sweep transaction
 *                          deletes, so a backlog is worked off in bounded steps
 *                          rather than one long-held lock
 */
public record MultipartPolicy(Duration sessionTtl, long minPartSizeBytes, int sweepBatchSize) {

    public MultipartPolicy {
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("Multipart session TTL must be positive");
        }
        if (minPartSizeBytes < 1) {
            throw new IllegalArgumentException("Minimum part size must be at least one byte");
        }
        if (sweepBatchSize < 1) {
            throw new IllegalArgumentException("Sweep batch size must be at least one");
        }
    }
}
