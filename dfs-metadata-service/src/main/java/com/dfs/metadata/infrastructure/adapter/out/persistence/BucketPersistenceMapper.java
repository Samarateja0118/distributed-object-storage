package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;

final class BucketPersistenceMapper {

    private BucketPersistenceMapper() {
    }

    static Bucket toDomain(BucketEntity entity) {
        return new Bucket(
                entity.getId(),
                entity.getTenantId(),
                new BucketName(entity.getName()),
                entity.isVersioningEnabled(),
                entity.getDefaultEncryption(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static BucketEntity toEntity(Bucket bucket) {
        return new BucketEntity(
                bucket.id(),
                bucket.tenantId(),
                bucket.name().value(),
                bucket.versioningEnabled(),
                bucket.defaultEncryption(),
                bucket.createdAt(),
                bucket.updatedAt(),
                bucket.deletedAt());
    }
}
