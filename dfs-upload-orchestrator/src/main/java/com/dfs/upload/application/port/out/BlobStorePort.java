package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;

/**
 * The content half of an object, one chunk at a time, on a named blob node.
 *
 * <p>Chunks are addressed by digest, so there is no tenant or bucket in this
 * interface at all — ownership lives with the metadata service, and identical
 * bytes from two tenants are one stored chunk.
 *
 * <p>The node is named by the caller rather than chosen here. Choosing where a
 * chunk goes is placement policy, which belongs to the application layer; and on
 * a read the node is whatever was recorded when the chunk was written, which only
 * the caller knows (ADR 11).
 */
public interface BlobStorePort {

    /**
     * Idempotent: the digest names the content, so storing the same chunk twice
     * on a node is indistinguishable from storing it once. That is what makes a
     * retried upload safe without a transaction log.
     *
     * @throws com.dfs.upload.domain.exception.DownstreamUnavailableException if
     *         the node did not answer, which is the one failure worth trying
     *         another node for
     * @throws com.dfs.upload.domain.exception.BlobNodeNotConfiguredException if
     *         no node with this id is configured
     */
    void store(String nodeId, String digest, byte[] content, CallerToken token);

    /**
     * One chunk's bytes from the node it was recorded on. Bounded by the
     * configured chunk size, which is what keeps a whole object from having to
     * fit in memory to be served.
     *
     * @throws com.dfs.upload.domain.exception.BlobNodeNotConfiguredException if
     *         no node with this id is configured
     */
    byte[] fetch(String nodeId, String digest, CallerToken token);
}
