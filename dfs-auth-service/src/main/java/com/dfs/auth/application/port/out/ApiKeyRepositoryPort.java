package com.dfs.auth.application.port.out;

import com.dfs.auth.domain.model.ApiKey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for API key persistence.
 */
public interface ApiKeyRepositoryPort {

    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findByPrefix(String keyPrefix);

    Optional<ApiKey> findByTenantAndId(UUID tenantId, UUID apiKeyId);

    List<ApiKey> findAllByTenant(UUID tenantId);

    /**
     * Recorded outside the verification transaction: a failed usage stamp must
     * never turn an otherwise valid authentication into an error.
     */
    void touchLastUsed(UUID apiKeyId, Instant usedAt);
}
