package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

/**
 * Deliberately carries no detail about which half of the credential pair was
 * wrong: distinguishing them would let an attacker enumerate accounts.
 */
public class InvalidCredentialsException extends DfsException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
