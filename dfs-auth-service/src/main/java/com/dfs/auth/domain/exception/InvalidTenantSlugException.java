package com.dfs.auth.domain.exception;

public class InvalidTenantSlugException extends AuthValidationException {

    public InvalidTenantSlugException(String message) {
        super(message);
    }
}
