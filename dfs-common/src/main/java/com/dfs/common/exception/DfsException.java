package com.dfs.common.exception;

/**
 * Base runtime exception for domain and application errors.
 * Business-specific subclasses will be introduced with feature implementation.
 */
public class DfsException extends RuntimeException {

    public DfsException(String message) {
        super(message);
    }

    public DfsException(String message, Throwable cause) {
        super(message, cause);
    }
}
