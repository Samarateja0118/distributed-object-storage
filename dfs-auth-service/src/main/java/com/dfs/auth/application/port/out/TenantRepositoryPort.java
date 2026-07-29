package com.dfs.auth.application.port.out;

import com.dfs.auth.domain.model.Tenant;
import com.dfs.auth.domain.model.TenantSlug;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for tenant persistence. Lookups exclude soft-deleted rows.
 */
public interface TenantRepositoryPort {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID tenantId);

    boolean existsBySlug(TenantSlug slug);
}
