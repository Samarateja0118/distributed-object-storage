package com.dfs.auth.domain.model;

import java.util.UUID;

/**
 * The identity behind a request, however it authenticated. Password login and
 * API keys both collapse to this before a token is minted, so downstream
 * services never need to know which path was used.
 */
public record Principal(UUID tenantId, UUID userId, EmailAddress email, UserRole role) {

    public static Principal of(User user) {
        return new Principal(user.tenantId(), user.id(), user.email(), user.role());
    }
}
