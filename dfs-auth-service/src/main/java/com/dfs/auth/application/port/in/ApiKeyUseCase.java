package com.dfs.auth.application.port.in;

import com.dfs.auth.domain.model.ApiKey;
import com.dfs.auth.domain.model.IssuedApiKey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port for API key lifecycle.
 */
public interface ApiKeyUseCase {

    IssuedApiKey createApiKey(CreateApiKeyCommand command);

    List<ApiKey> listApiKeys(UUID tenantId);

    void revokeApiKey(UUID tenantId, UUID apiKeyId);

    record CreateApiKeyCommand(
            UUID tenantId,
            UUID userId,
            String name,
            Instant expiresAt) {
    }
}
