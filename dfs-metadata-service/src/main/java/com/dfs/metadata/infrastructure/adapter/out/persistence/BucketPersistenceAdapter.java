package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.application.port.out.BucketRepositoryPort;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class BucketPersistenceAdapter implements BucketRepositoryPort {

    private final BucketJpaRepository repository;

    BucketPersistenceAdapter(BucketJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Bucket save(Bucket bucket) {
        BucketEntity entity = repository.findById(bucket.id())
                .orElseGet(() -> BucketPersistenceMapper.toEntity(bucket));
        entity.apply(bucket.versioningEnabled(), bucket.updatedAt(), bucket.deletedAt());
        return BucketPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Bucket> findByTenantAndName(UUID tenantId, BucketName name) {
        return repository.findByTenantIdAndNameAndDeletedAtIsNull(tenantId, name.value())
                .map(BucketPersistenceMapper::toDomain);
    }

    @Override
    public PageResult<Bucket> findAllByTenant(UUID tenantId, PageQuery pageQuery) {
        Page<BucketEntity> page = repository.findByTenantIdAndDeletedAtIsNull(
                tenantId,
                PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.ASC, "name")));
        List<Bucket> buckets = page.getContent().stream().map(BucketPersistenceMapper::toDomain).toList();
        return new PageResult<>(buckets, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public boolean existsByTenantAndName(UUID tenantId, BucketName name) {
        return repository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, name.value());
    }
}
