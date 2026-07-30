package com.dfs.blob.domain.exception;

import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.common.exception.DfsException;

public class BlobNotFoundException extends DfsException {

    public BlobNotFoundException(BlobDigest digest) {
        super("Blob '%s' is not stored on this node".formatted(digest));
    }
}
