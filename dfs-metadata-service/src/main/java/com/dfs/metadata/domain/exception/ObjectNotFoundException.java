package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

public class ObjectNotFoundException extends DfsException {

    public ObjectNotFoundException(String bucketName, String objectKey) {
        super("Object '%s' was not found in bucket '%s'".formatted(objectKey, bucketName));
    }
}
