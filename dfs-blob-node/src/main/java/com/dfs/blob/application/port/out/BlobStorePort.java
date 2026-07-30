package com.dfs.blob.application.port.out;

import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;

import java.io.InputStream;
import java.util.Optional;

/**
 * The physical store behind a node. Local disk in Phase 1; the port exists so a
 * different backing store does not reach into the API layer.
 */
public interface BlobStorePort {

    StoredBlob write(BlobDigest digest, InputStream content);

    Optional<StoredBlob> stat(BlobDigest digest);

    InputStream openStream(BlobDigest digest);
}
