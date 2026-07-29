package com.dfs.metadata.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a tenant's storage namespace.
 */
public record Bucket(
        UUID id,
        UUID tenantId,
        BucketName name,
        boolean versioningEnabled,
        EncryptionType defaultEncryption,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {

    public static Bucket create(
            UUID tenantId,
            BucketName name,
            boolean versioningEnabled,
            EncryptionType defaultEncryption,
            Instant now) {
        return new Bucket(UUID.randomUUID(), tenantId, name, versioningEnabled, defaultEncryption, now, now, null);
    }

    /**
     * Versioning can be suspended for new writes but never retroactively removes
     * existing versions, so re-enabling is always safe.
     */
    public Bucket withVersioning(boolean enabled, Instant now) {
        return new Bucket(id, tenantId, name, enabled, defaultEncryption, createdAt, now, deletedAt);
    }

    public Bucket markDeleted(Instant now) {
        return new Bucket(id, tenantId, name, versioningEnabled, defaultEncryption, createdAt, now, now);
    }
}
