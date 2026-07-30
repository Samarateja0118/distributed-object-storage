package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;

/**
 * The content half of an object. Blobs are addressed by digest, so there is no
 * tenant or bucket in this interface at all — ownership lives with the metadata
 * service, and identical bytes from two tenants are one blob.
 */
public interface BlobStorePort {

    /**
     * Idempotent: the digest names the content, so storing the same bytes twice
     * is indistinguishable from storing them once.
     */
    void store(String digest, byte[] content, CallerToken token);

    byte[] fetch(String digest, CallerToken token);
}
