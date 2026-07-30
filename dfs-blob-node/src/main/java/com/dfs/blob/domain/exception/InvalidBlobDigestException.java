package com.dfs.blob.domain.exception;

public class InvalidBlobDigestException extends BlobValidationException {

    public InvalidBlobDigestException(String message) {
        super(message);
    }
}
