package com.dfs.blob.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dfs.blob")
public record BlobNodeProperties(
        String nodeId,
        String storageRootPath,
        long capacityBytes,
        String rack,
        String zone
) {
}
