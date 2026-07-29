package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

public class BucketNotEmptyException extends DfsException {

    public BucketNotEmptyException(String bucketName) {
        super("Bucket '%s' still contains objects and cannot be deleted".formatted(bucketName));
    }
}
