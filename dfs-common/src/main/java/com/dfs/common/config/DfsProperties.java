package com.dfs.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base service identity properties shared by every deployable module.
 */
@ConfigurationProperties(prefix = "dfs")
public record DfsProperties(
        String serviceName,
        String environment
) {
}
