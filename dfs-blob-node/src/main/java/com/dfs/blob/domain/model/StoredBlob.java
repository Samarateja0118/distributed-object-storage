package com.dfs.blob.domain.model;

/**
 * A blob that is durably on disk, with the size the node actually holds.
 */
public record StoredBlob(BlobDigest digest, long sizeBytes) {
}
