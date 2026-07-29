package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.EncryptionType;

import java.time.Instant;
import java.util.UUID;

public record BucketResponse(
        UUID id,
        String name,
        boolean versioningEnabled,
        EncryptionType defaultEncryption,
        Instant createdAt,
        Instant updatedAt) {

    public static BucketResponse from(Bucket bucket) {
        return new BucketResponse(
                bucket.id(),
                bucket.name().value(),
                bucket.versioningEnabled(),
                bucket.defaultEncryption(),
                bucket.createdAt(),
                bucket.updatedAt());
    }
}
