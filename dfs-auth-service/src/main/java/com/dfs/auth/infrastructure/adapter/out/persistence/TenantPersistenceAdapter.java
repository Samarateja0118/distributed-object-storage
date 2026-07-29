package com.dfs.auth.infrastructure.adapter.out.persistence;

import com.dfs.auth.application.port.out.TenantRepositoryPort;
import com.dfs.auth.domain.model.Tenant;
import com.dfs.auth.domain.model.TenantSlug;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class TenantPersistenceAdapter implements TenantRepositoryPort {

    private final TenantJpaRepository repository;

    TenantPersistenceAdapter(TenantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = repository.findById(tenant.id())
                .orElseGet(() -> AuthPersistenceMapper.toEntity(tenant));
        entity.apply(tenant.name(), tenant.updatedAt(), tenant.deletedAt());
        return AuthPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Tenant> findById(UUID tenantId) {
        return repository.findByIdAndDeletedAtIsNull(tenantId).map(AuthPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(TenantSlug slug) {
        return repository.existsBySlugAndDeletedAtIsNull(slug.value());
    }
}
