package com.dfs.blob.application.port.in;

import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;

import java.io.InputStream;
import java.util.Optional;

public interface BlobStorageUseCase {

    /**
     * Stores content under the digest the caller claims it has, after verifying
     * that claim. Idempotent: storing bytes that are already present succeeds
     * without rewriting them.
     */
    StoredBlob store(BlobDigest digest, InputStream content);

    Optional<StoredBlob> stat(BlobDigest digest);

    /**
     * Opens the stored bytes for reading. The caller owns the stream and must
     * close it.
     */
    InputStream open(BlobDigest digest);
}
