package com.dfs.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dfs.auth")
public record AuthProperties(
        Jwt jwt,
        Duration apiKeyCacheTtl
) {
    public record Jwt(
            String issuer,
            Duration accessTokenTtl,
            String signingSecret
    ) {
    }
}
