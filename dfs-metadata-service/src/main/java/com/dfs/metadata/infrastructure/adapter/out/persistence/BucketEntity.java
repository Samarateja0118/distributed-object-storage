package com.dfs.metadata.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import com.dfs.metadata.domain.model.EncryptionType;

@Entity
@Table(name = "buckets")
public class BucketEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 63)
    private String name;

    @Column(name = "versioning_enabled", nullable = false)
    private boolean versioningEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_encryption", nullable = false, length = 32)
    private EncryptionType defaultEncryption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BucketEntity() {
    }

    BucketEntity(
            UUID id,
            UUID tenantId,
            String name,
            boolean versioningEnabled,
            EncryptionType defaultEncryption,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.versioningEnabled = versioningEnabled;
        this.defaultEncryption = defaultEncryption;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getName() {
        return name;
    }

    boolean isVersioningEnabled() {
        return versioningEnabled;
    }

    EncryptionType getDefaultEncryption() {
        return defaultEncryption;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void apply(boolean newVersioningEnabled, Instant newUpdatedAt, Instant newDeletedAt) {
        this.versioningEnabled = newVersioningEnabled;
        this.updatedAt = newUpdatedAt;
        this.deletedAt = newDeletedAt;
    }
}
