package com.dfs.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "dfs.gateway")
public record GatewayProperties(
        List<ServiceRoute> routes,
        Duration requestTimeout,
        int maxRequestSizeMb
) {
    public record ServiceRoute(
            String id,
            String pathPrefix,
            String upstreamUrl
    ) {
    }
}
