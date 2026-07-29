package com.dfs.metadata.application.port.out;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for bucket persistence. All lookups are scoped to a tenant and
 * exclude soft-deleted rows.
 */
public interface BucketRepositoryPort {

    Bucket save(Bucket bucket);

    Optional<Bucket> findByTenantAndName(UUID tenantId, BucketName name);

    PageResult<Bucket> findAllByTenant(UUID tenantId, PageQuery pageQuery);

    boolean existsByTenantAndName(UUID tenantId, BucketName name);
}
