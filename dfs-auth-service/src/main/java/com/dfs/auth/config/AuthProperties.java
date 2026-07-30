package com.dfs.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Auth-only knobs. The issuer and signing secret deliberately live in
 * {@link com.dfs.common.security.DfsJwtProperties} instead, since every service
 * that validates a token has to agree with the one that mints it.
 */
@ConfigurationProperties(prefix = "dfs.auth")
public record AuthProperties(
        Jwt jwt,
        Duration apiKeyCacheTtl
) {
    public record Jwt(
            Duration accessTokenTtl
    ) {
    }
}
