package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

/**
 * Raised when a value object rejects its input. Maps to HTTP 400.
 */
public class AuthValidationException extends DfsException {

    public AuthValidationException(String message) {
        super(message);
    }
}
