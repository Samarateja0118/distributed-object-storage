package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;

/**
 * The content half of an object, one chunk at a time. Chunks are addressed by
 * digest, so there is no tenant or bucket in this interface at all — ownership
 * lives with the metadata service, and identical bytes from two tenants are one
 * stored chunk.
 */
public interface BlobStorePort {

    /**
     * Idempotent: the digest names the content, so storing the same chunk twice
     * is indistinguishable from storing it once. That is what makes a retried
     * upload safe without a transaction log.
     */
    void store(String digest, byte[] content, CallerToken token);

    /**
     * One chunk's bytes. Bounded by the configured chunk size, which is what
     * keeps a whole object from having to fit in memory to be served.
     */
    byte[] fetch(String digest, CallerToken token);
}
