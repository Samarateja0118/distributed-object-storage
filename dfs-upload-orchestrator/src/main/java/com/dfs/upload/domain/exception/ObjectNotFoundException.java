package com.dfs.upload.domain.exception;

public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException(String bucketName, String objectKey) {
        super("Object '%s' was not found in bucket '%s'".formatted(objectKey, bucketName));
    }

    public ObjectNotFoundException(String message) {
        super(message);
    }
}
