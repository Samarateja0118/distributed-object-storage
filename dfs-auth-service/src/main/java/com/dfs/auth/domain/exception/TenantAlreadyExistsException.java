package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

public class TenantAlreadyExistsException extends DfsException {

    public TenantAlreadyExistsException(String slug) {
        super("Tenant '%s' already exists".formatted(slug));
    }
}
