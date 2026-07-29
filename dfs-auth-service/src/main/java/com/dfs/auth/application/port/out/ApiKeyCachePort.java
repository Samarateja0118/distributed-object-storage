package com.dfs.auth.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the API key verification cache.
 *
 * <p>Entries are keyed by prefix rather than by the key itself so a revocation
 * can evict them — the plaintext is not available at revoke time. The digest of
 * the verified key is carried inside the entry and re-checked on every hit, so a
 * cached entry can never authenticate a different key sharing the prefix.
 */
public interface ApiKeyCachePort {

    Optional<CachedApiKey> find(String keyPrefix);

    void put(String keyPrefix, CachedApiKey entry);

    void evict(String keyPrefix);

    /**
     * Flat by design: primitives survive a JSON round trip through Redis without
     * needing custom deserializers for the domain's value objects.
     */
    record CachedApiKey(
            String secretSha256,
            UUID tenantId,
            UUID userId,
            String email,
            String role) {
    }
}
