package com.dfs.blob.domain.exception;

import com.dfs.common.exception.DfsException;

/**
 * Raised when a value object rejects its input. Maps to HTTP 400.
 */
public class BlobValidationException extends DfsException {

    public BlobValidationException(String message) {
        super(message);
    }
}
