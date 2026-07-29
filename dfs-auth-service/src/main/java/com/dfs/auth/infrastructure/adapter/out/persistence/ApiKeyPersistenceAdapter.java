package com.dfs.auth.infrastructure.adapter.out.persistence;

import com.dfs.auth.application.port.out.ApiKeyRepositoryPort;
import com.dfs.auth.domain.model.ApiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ApiKeyPersistenceAdapter implements ApiKeyRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPersistenceAdapter.class);

    private final ApiKeyJpaRepository repository;

    ApiKeyPersistenceAdapter(ApiKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        ApiKeyEntity entity = repository.findById(apiKey.id())
                .orElseGet(() -> AuthPersistenceMapper.toEntity(apiKey));
        entity.apply(apiKey.expiresAt(), apiKey.lastUsedAt(), apiKey.revokedAt());
        return AuthPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<ApiKey> findByPrefix(String keyPrefix) {
        return repository.findByKeyPrefix(keyPrefix).map(AuthPersistenceMapper::toDomain);
    }

    @Override
    public Optional<ApiKey> findByTenantAndId(UUID tenantId, UUID apiKeyId) {
        return repository.findByIdAndTenantId(apiKeyId, tenantId).map(AuthPersistenceMapper::toDomain);
    }

    @Override
    public List<ApiKey> findAllByTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(AuthPersistenceMapper::toDomain)
                .toList();
    }

    /**
     * Runs in its own transaction and swallows failures: the caller is on the
     * authentication path, which is read-only, and a bookkeeping write must not
     * be able to reject a valid credential.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchLastUsed(UUID apiKeyId, Instant usedAt) {
        try {
            repository.updateLastUsedAt(apiKeyId, usedAt);
        } catch (RuntimeException e) {
            log.warn("Could not record last use of API key {}", apiKeyId, e);
        }
    }
}
