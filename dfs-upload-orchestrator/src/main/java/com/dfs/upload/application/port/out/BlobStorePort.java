package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;

/**
 * The content half of an object, one chunk at a time on one named node.
 *
 * <p>Chunks are addressed by digest, so there is no tenant or bucket in this
 * interface at all — ownership lives with the metadata service, and identical
 * bytes from two tenants are one stored chunk.
 *
 * <p>The node is a parameter rather than something this port decides. Choosing
 * where a chunk goes is a policy question (ADR 11) and belongs above the
 * transport; what happens here is only "put these bytes on that node".
 */
public interface BlobStorePort {

    /**
     * Idempotent: the digest names the content, so storing the same chunk twice
     * is indistinguishable from storing it once. That is what makes a retried
     * upload safe without a transaction log — and what makes a write that failed
     * over to another node harmless if the first node had in fact received it.
     */
    void store(String nodeId, String digest, byte[] content, CallerToken token);

    /**
     * One chunk's bytes from the node recorded against it. Bounded by the
     * configured chunk size, which is what keeps a whole object from having to
     * fit in memory to be served.
     */
    byte[] fetch(String nodeId, String digest, CallerToken token);
}
