package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;

final class ObjectPersistenceMapper {

    private ObjectPersistenceMapper() {
    }

    static StorageObject toDomain(ObjectEntity entity) {
        return new StorageObject(
                entity.getId(),
                entity.getBucketId(),
                new ObjectKey(entity.getObjectKey()),
                entity.getVersionId(),
                entity.isLatest(),
                entity.getSizeBytes(),
                entity.getContentType(),
                new ContentHash(entity.getContentHash()),
                entity.getEtag(),
                entity.getUserMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static ObjectEntity toEntity(StorageObject object) {
        return new ObjectEntity(
                object.id(),
                object.bucketId(),
                object.key().value(),
                object.versionId(),
                object.latest(),
                object.sizeBytes(),
                object.contentType(),
                object.contentHash().value(),
                object.etag(),
                object.userMetadata(),
                object.createdAt(),
                object.updatedAt(),
                object.deletedAt());
    }
}
