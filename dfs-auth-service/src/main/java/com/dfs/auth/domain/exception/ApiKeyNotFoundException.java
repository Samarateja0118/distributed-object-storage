package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

import java.util.UUID;

public class ApiKeyNotFoundException extends DfsException {

    public ApiKeyNotFoundException(UUID apiKeyId) {
        super("API key '%s' was not found".formatted(apiKeyId));
    }
}
