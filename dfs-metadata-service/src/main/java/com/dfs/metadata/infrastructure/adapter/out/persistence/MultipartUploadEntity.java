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
@Table(name = "multipart_uploads")
public class MultipartUploadEntity {

    @Id
    private UUID id;

    @Column(name = "bucket_id", nullable = false)
    private UUID bucketId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_metadata", nullable = false)
    private Map<String, String> userMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected MultipartUploadEntity() {
    }

    MultipartUploadEntity(UUID id, UUID bucketId, String objectKey, String contentType,
                          Map<String, String> userMetadata, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.bucketId = bucketId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.userMetadata = userMetadata;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
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

    String getContentType() {
        return contentType;
    }

    Map<String, String> getUserMetadata() {
        return userMetadata;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
