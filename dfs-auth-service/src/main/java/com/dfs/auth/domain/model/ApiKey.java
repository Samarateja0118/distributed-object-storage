package com.dfs.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived credential for machine callers. Only the prefix and a hash of the
 * secret are stored, so a database leak does not yield usable keys.
 */
public record ApiKey(
        UUID id,
        UUID tenantId,
        UUID userId,
        String name,
        String keyPrefix,
        String secretHash,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt
) {

    public static ApiKey create(
            UUID tenantId,
            UUID userId,
            String name,
            String keyPrefix,
            String secretHash,
            Instant now,
            Instant expiresAt) {
        return new ApiKey(UUID.randomUUID(), tenantId, userId, name, keyPrefix, secretHash, now, expiresAt, null, null);
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public ApiKey markRevoked(Instant now) {
        return new ApiKey(id, tenantId, userId, name, keyPrefix, secretHash, createdAt, expiresAt, lastUsedAt, now);
    }
}
