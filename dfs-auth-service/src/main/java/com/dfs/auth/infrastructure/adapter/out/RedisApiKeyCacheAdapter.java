package com.dfs.auth.infrastructure.adapter.out;

import com.dfs.auth.application.port.out.ApiKeyCachePort;
import com.dfs.auth.config.AuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Caches verified API keys so the bcrypt comparison is not repeated on every
 * request.
 *
 * <p>Every Redis failure degrades to a cache miss rather than an error: the
 * database path is always able to answer, and an unavailable cache must not take
 * authentication down with it.
 */
@Component
class RedisApiKeyCacheAdapter implements ApiKeyCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisApiKeyCacheAdapter.class);
    private static final String KEY_NAMESPACE = "dfs:auth:apikey:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    RedisApiKeyCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper, AuthProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = properties.apiKeyCacheTtl() == null ? Duration.ofMinutes(5) : properties.apiKeyCacheTtl();
    }

    @Override
    public Optional<CachedApiKey> find(String keyPrefix) {
        try {
            String payload = redis.opsForValue().get(KEY_NAMESPACE + keyPrefix);
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, CachedApiKey.class));
        } catch (DataAccessException | JsonProcessingException e) {
            log.debug("API key cache lookup failed, falling back to the database", e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String keyPrefix, CachedApiKey entry) {
        try {
            redis.opsForValue().set(KEY_NAMESPACE + keyPrefix, objectMapper.writeValueAsString(entry), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.debug("Could not cache API key verification", e);
        }
    }

    /**
     * A failure here is logged at warn, not debug: it means a revoked key stays
     * usable until its entry expires.
     */
    @Override
    public void evict(String keyPrefix) {
        try {
            redis.delete(KEY_NAMESPACE + keyPrefix);
        } catch (DataAccessException e) {
            log.warn("Could not evict cached API key {}; it stays valid for up to {}", keyPrefix, ttl, e);
        }
    }
}
