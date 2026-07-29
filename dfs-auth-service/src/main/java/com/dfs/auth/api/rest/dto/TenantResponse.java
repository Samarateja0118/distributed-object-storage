package com.dfs.auth.api.rest.dto;

import com.dfs.auth.domain.model.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        Instant createdAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.id(), tenant.name(), tenant.slug().value(), tenant.createdAt());
    }
}
