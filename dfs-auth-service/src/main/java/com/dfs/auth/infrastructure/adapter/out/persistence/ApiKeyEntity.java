package com.dfs.auth.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "secret_hash", nullable = false, length = 72)
    private String secretHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKeyEntity() {
    }

    ApiKeyEntity(
            UUID id,
            UUID tenantId,
            UUID userId,
            String name,
            String keyPrefix,
            String secretHash,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.secretHash = secretHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getUserId() {
        return userId;
    }

    String getName() {
        return name;
    }

    String getKeyPrefix() {
        return keyPrefix;
    }

    String getSecretHash() {
        return secretHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    void apply(Instant newExpiresAt, Instant newLastUsedAt, Instant newRevokedAt) {
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = newLastUsedAt;
        this.revokedAt = newRevokedAt;
    }
}
