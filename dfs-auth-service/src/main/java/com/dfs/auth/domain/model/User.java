package com.dfs.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who can authenticate. The password hash never leaves this record —
 * comparison happens in the application layer, which owns the encoder.
 */
public record User(
        UUID id,
        UUID tenantId,
        EmailAddress email,
        String passwordHash,
        UserRole role,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {

    public static User create(UUID tenantId, EmailAddress email, String passwordHash, UserRole role, Instant now) {
        return new User(UUID.randomUUID(), tenantId, email, passwordHash, role, now, now, null);
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public User markDeleted(Instant now) {
        return new User(id, tenantId, email, passwordHash, role, createdAt, now, now);
    }
}
