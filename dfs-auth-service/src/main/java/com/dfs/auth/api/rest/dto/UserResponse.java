package com.dfs.auth.api.rest.dto;

import com.dfs.auth.domain.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Carries no password material of any kind, not even the hash.
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String role,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(), user.tenantId(), user.email().value(), user.role().name(), user.createdAt());
    }
}
