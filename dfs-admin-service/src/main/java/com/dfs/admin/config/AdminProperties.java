package com.dfs.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dfs.admin")
public record AdminProperties(
        boolean allowNodeRegistration,
        long defaultTenantQuotaBytes
) {
}
