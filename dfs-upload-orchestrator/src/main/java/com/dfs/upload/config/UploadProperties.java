package com.dfs.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dfs.upload")
public record UploadProperties(
        int chunkSizeMb,
        int maxConcurrentChunks,
        Duration multipartSessionTtl,
        String metadataGrpcTarget,
        String blobGrpcTarget
) {
}
