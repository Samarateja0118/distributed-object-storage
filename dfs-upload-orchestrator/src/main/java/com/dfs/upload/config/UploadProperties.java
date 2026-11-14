package com.dfs.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * @param blobNodes         The static blob node set chunks are placed across
 *                          (ADR 11). Each node's gRPC surface is how the byte
 *                          path travels (ADR 10). Adding a node is a redeploy:
 *                          a dynamic registry belongs with the admin service.
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
        List<BlobNode> blobNodes,
        String metadataBaseUrl,
        DataSize maxObjectSize,
        Duration downstreamTimeout
) {

    /**
     * One blob node.
     *
     * @param id     The name recorded against every chunk written to this node,
     *               and what reads use to find it again. It must never be
     *               reused for a different node or renamed while chunks are
     *               recorded on it: the metadata service keeps the id, not the
     *               address, so a renamed node strands every chunk on it.
     * @param target The node's gRPC address, in the form the gRPC client
     *               accepts — {@code static://host:port}, {@code dns:///host:port},
     *               or {@code in-process:name} in tests.
     */
    public record BlobNode(String id, String target) {
    }
}
