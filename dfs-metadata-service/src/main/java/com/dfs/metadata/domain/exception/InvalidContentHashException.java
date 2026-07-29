package com.dfs.metadata.domain.exception;

public class InvalidContentHashException extends MetadataValidationException {

    public InvalidContentHashException(String message) {
        super(message);
    }
}
