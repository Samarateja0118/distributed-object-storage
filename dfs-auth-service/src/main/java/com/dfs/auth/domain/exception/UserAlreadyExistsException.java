package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

public class UserAlreadyExistsException extends DfsException {

    public UserAlreadyExistsException(String email) {
        super("A user with email '%s' already exists".formatted(email));
    }
}
