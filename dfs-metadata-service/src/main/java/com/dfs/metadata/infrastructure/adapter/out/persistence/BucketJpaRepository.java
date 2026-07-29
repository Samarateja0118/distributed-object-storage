package com.dfs.metadata.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BucketJpaRepository extends JpaRepository<BucketEntity, UUID> {

    Optional<BucketEntity> findByTenantIdAndNameAndDeletedAtIsNull(UUID tenantId, String name);

    Page<BucketEntity> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(UUID tenantId, String name);
}
