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
 * @param chunkSize         Size of every chunk but an object's last. Part of the
 *                          contract rather than a free tuning knob: the same
 *                          bytes cut at a different size produce a different
 *                          composite digest, so changing this changes the
 *                          identity of every object written afterwards (ADR 9).
 * @param blobNodeId        Which blob node chunks are written to, recorded
 *                          against each chunk so reads follow where the bytes
 *                          actually went (ADR 11). One node until placement
 *                          arrives.
 * @param maxObjectSize     Ceiling on a single upload. Content is streamed a
 *                          chunk at a time, so this caps how large an object may
 *                          be rather than how much heap one request can take.
 * @param downstreamTimeout Applied to both downstream calls — a read timeout on
 *                          the REST client, a deadline on the gRPC one — so a
 *                          hung dependency fails the request rather than pinning
 *                          a thread indefinitely.
 */
@ConfigurationProperties(prefix = "dfs.upload")
public record UploadProperties(
        DataSize chunkSize,
        int maxConcurrentChunks,
        Duration multipartSessionTtl,
        String metadataGrpcTarget,
        String blobGrpcTarget,
        String metadataBaseUrl,
        String blobNodeId,
        DataSize maxObjectSize,
        Duration downstreamTimeout
) {
}
