package com.dfs.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * @param blobGrpcTarget    Address of the blob node's gRPC surface, which is how
 *                          the byte path travels from Phase 2 (ADR 10). Read
 *                          through the {@code grpc.client.blob-node} block that
 *                          configures the channel.
 * @param metadataGrpcTarget Scaffolding: the metadata service stays on REST, so
 *                          nothing reads this yet.
 * @param metadataBaseUrl   REST base URL of the metadata service.
 * @param maxObjectSize     Ceiling on a single upload. An object is still
 *                          buffered whole in memory in order to digest it before
 *                          writing, so without a limit one request could exhaust
 *                          the heap.
 * @param downstreamTimeout Applied to both downstream calls — a read timeout on
 *                          the REST client, a deadline on the gRPC one — so a
 *                          hung dependency fails the request rather than pinning
 *                          a thread indefinitely.
 */
@ConfigurationProperties(prefix = "dfs.upload")
public record UploadProperties(
        int chunkSizeMb,
        int maxConcurrentChunks,
        Duration multipartSessionTtl,
        String metadataGrpcTarget,
        String blobGrpcTarget,
        String metadataBaseUrl,
        DataSize maxObjectSize,
        Duration downstreamTimeout
) {
}
