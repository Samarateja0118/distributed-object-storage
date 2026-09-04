package com.dfs.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * @param blobNodes         The cluster's blob nodes, by id. Each id must have a
 *                          matching {@code grpc.client.<id>.address} block, which
 *                          is where its gRPC target is configured (ADR 10).
 *                          <p>This is the node set rendezvous hashing ranks over
 *                          (ADR 11), and it is static on purpose: a dynamic
 *                          registry belongs with the admin service, and inventing
 *                          one here would put cluster membership in the component
 *                          least ready to own it. Adding a node is a redeploy.
 *                          <p>Removing an id from this list does not move its
 *                          chunks. They are still recorded against it and reads
 *                          still ask for it by name, so an id that has ever held
 *                          a chunk must stay configured until those chunks are
 *                          moved — which is Phase 3's job.
 * @param metadataGrpcTarget Scaffolding: the metadata service stays on REST, so
 *                          nothing reads this yet.
 * @param metadataBaseUrl   REST base URL of the metadata service.
 * @param chunkSize         Size of every chunk but an object's last. Part of the
 *                          contract rather than a free tuning knob: the same
 *                          bytes cut at a different size produce a different
 *                          composite digest, so changing this changes the
 *                          identity of every object written afterwards (ADR 9).
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
        String metadataBaseUrl,
        List<String> blobNodes,
        DataSize maxObjectSize,
        Duration downstreamTimeout
) {
}
