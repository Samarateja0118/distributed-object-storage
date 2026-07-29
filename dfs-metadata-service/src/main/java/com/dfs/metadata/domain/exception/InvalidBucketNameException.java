package com.dfs.metadata.domain.exception;

public class InvalidBucketNameException extends MetadataValidationException {

    public InvalidBucketNameException(String message) {
        super(message);
    }
}
