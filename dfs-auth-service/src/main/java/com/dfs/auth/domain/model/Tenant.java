package com.dfs.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root owning users, API keys, and — in the metadata service — buckets.
 */
public record Tenant(
        UUID id,
        String name,
        TenantSlug slug,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {

    public static Tenant create(String name, TenantSlug slug, Instant now) {
        return new Tenant(UUID.randomUUID(), name, slug, now, now, null);
    }

    public Tenant markDeleted(Instant now) {
        return new Tenant(id, name, slug, createdAt, now, now);
    }
}
