package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.application.port.out.ObjectMetadataRepositoryPort;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ObjectPersistenceAdapter implements ObjectMetadataRepositoryPort {

    private final ObjectJpaRepository repository;
    private final Clock clock;

    ObjectPersistenceAdapter(ObjectJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public StorageObject save(StorageObject storageObject) {
        ObjectEntity entity = repository.findById(storageObject.id())
                .orElseGet(() -> ObjectPersistenceMapper.toEntity(storageObject));
        entity.apply(
                storageObject.versionId(),
                storageObject.latest(),
                storageObject.sizeBytes(),
                storageObject.contentType(),
                storageObject.contentHash().value(),
                storageObject.etag(),
                storageObject.userMetadata(),
                storageObject.updatedAt(),
                storageObject.deletedAt());
        return ObjectPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<StorageObject> findCurrentVersion(UUID bucketId, ObjectKey key) {
        return repository.findByBucketIdAndObjectKeyAndLatestTrueAndDeletedAtIsNull(bucketId, key.value())
                .map(ObjectPersistenceMapper::toDomain);
    }

    @Override
    public List<StorageObject> findAllVersions(UUID bucketId, ObjectKey key) {
        return repository.findByBucketIdAndObjectKeyAndDeletedAtIsNullOrderByCreatedAtDesc(bucketId, key.value())
                .stream()
                .map(ObjectPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<StorageObject> findCurrentVersionsByPrefix(UUID bucketId, String prefix, PageQuery pageQuery) {
        Page<ObjectEntity> page = repository.findByBucketIdAndObjectKeyStartingWithAndLatestTrueAndDeletedAtIsNull(
                bucketId,
                prefix == null ? "" : prefix,
                PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.ASC, "objectKey")));
        List<StorageObject> objects = page.getContent().stream().map(ObjectPersistenceMapper::toDomain).toList();
        return new PageResult<>(objects, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public boolean existsInBucket(UUID bucketId) {
        return repository.existsByBucketIdAndDeletedAtIsNull(bucketId);
    }

    @Override
    public int softDeleteAllVersions(UUID bucketId, ObjectKey key) {
        return repository.softDeleteAllVersions(bucketId, key.value(), Instant.now(clock));
    }

    @Override
    public void demoteCurrentVersion(UUID bucketId, ObjectKey key) {
        repository.demoteCurrentVersion(bucketId, key.value(), Instant.now(clock));
    }
}
