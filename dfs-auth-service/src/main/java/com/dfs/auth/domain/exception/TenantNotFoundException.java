package com.dfs.auth.domain.exception;

import com.dfs.common.exception.DfsException;

import java.util.UUID;

public class TenantNotFoundException extends DfsException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant '%s' was not found".formatted(tenantId));
    }
}
