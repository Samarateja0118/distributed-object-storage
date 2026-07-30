package com.dfs.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * @param blobBaseUrl       REST base URL of the blob node. The gRPC targets
 *                          alongside it stay unused until Phase 2.
 * @param metadataBaseUrl   REST base URL of the metadata service.
 * @param maxObjectSize     Ceiling on a single upload. Phase 1 buffers a whole
 *                          object in memory in order to digest it before
 *                          writing, so without a limit one request could
 *                          exhaust the heap.
 * @param downstreamTimeout Applied to both downstream calls, so a hung
 *                          dependency fails the request rather than pinning a
 *                          thread indefinitely.
 */
@ConfigurationProperties(prefix = "dfs.upload")
public record UploadProperties(
        int chunkSizeMb,
        int maxConcurrentChunks,
        Duration multipartSessionTtl,
        String metadataGrpcTarget,
        String blobGrpcTarget,
        String blobBaseUrl,
        String metadataBaseUrl,
        DataSize maxObjectSize,
        Duration downstreamTimeout
) {
}
