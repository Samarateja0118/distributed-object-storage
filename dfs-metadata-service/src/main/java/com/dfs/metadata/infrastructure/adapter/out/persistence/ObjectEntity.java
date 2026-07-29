package com.dfs.metadata.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "objects")
public class ObjectEntity {

    @Id
    private UUID id;

    @Column(name = "bucket_id", nullable = false)
    private UUID bucketId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "is_latest", nullable = false)
    private boolean latest;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 64)
    private String etag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_metadata", nullable = false)
    private Map<String, String> userMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ObjectEntity() {
    }

    ObjectEntity(
            UUID id,
            UUID bucketId,
            String objectKey,
            UUID versionId,
            boolean latest,
            long sizeBytes,
            String contentType,
            String contentHash,
            String etag,
            Map<String, String> userMetadata,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.bucketId = bucketId;
        this.objectKey = objectKey;
        this.versionId = versionId;
        this.latest = latest;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.contentHash = contentHash;
        this.etag = etag;
        this.userMetadata = userMetadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getBucketId() {
        return bucketId;
    }

    String getObjectKey() {
        return objectKey;
    }

    UUID getVersionId() {
        return versionId;
    }

    boolean isLatest() {
        return latest;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getContentType() {
        return contentType;
    }

    String getContentHash() {
        return contentHash;
    }

    String getEtag() {
        return etag;
    }

    Map<String, String> getUserMetadata() {
        return userMetadata;
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

    void apply(
            UUID newVersionId,
            boolean newLatest,
            long newSizeBytes,
            String newContentType,
            String newContentHash,
            String newEtag,
            Map<String, String> newUserMetadata,
            Instant newUpdatedAt,
            Instant newDeletedAt) {
        this.versionId = newVersionId;
        this.latest = newLatest;
        this.sizeBytes = newSizeBytes;
        this.contentType = newContentType;
        this.contentHash = newContentHash;
        this.etag = newEtag;
        this.userMetadata = newUserMetadata;
        this.updatedAt = newUpdatedAt;
        this.deletedAt = newDeletedAt;
    }
}
