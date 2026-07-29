package com.dfs.auth.domain.exception;

public class InvalidEmailAddressException extends AuthValidationException {

    public InvalidEmailAddressException(String message) {
        super(message);
    }
}
