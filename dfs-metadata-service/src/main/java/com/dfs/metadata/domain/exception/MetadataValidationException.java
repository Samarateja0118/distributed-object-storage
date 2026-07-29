package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

/**
 * Raised when a value object rejects its input. Maps to HTTP 400.
 */
public class MetadataValidationException extends DfsException {

    public MetadataValidationException(String message) {
        super(message);
    }
}
