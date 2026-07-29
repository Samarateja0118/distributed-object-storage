package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

public class BucketAlreadyExistsException extends DfsException {

    public BucketAlreadyExistsException(String bucketName) {
        super("Bucket '%s' already exists".formatted(bucketName));
    }
}
