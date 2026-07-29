package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

public class BucketNotFoundException extends DfsException {

    public BucketNotFoundException(String bucketName) {
        super("Bucket '%s' was not found".formatted(bucketName));
    }
}
