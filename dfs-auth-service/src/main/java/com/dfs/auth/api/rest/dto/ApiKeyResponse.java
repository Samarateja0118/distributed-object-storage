package com.dfs.auth.api.rest.dto;

import com.dfs.auth.domain.model.ApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Lists an existing key. The secret is absent by design — it exists only in the
 * {@link IssuedApiKeyResponse} returned at creation time.
 */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.id(),
                apiKey.name(),
                apiKey.keyPrefix(),
                apiKey.createdAt(),
                apiKey.expiresAt(),
                apiKey.lastUsedAt(),
                apiKey.revokedAt());
    }
}
