package com.dfs.auth.api.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The slug pattern mirrors {@code TenantSlug}, which remains the real guard.
 * Duplicating it here is what turns a malformed slug into a field-level error
 * instead of an opaque 400 raised from the domain.
 */
public record RegisterTenantRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(min = 3, max = 63)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
                message = "must contain only lowercase letters, digits, and hyphens")
        String slug,
        @NotBlank @Email @Size(max = 320) String ownerEmail,
        @NotBlank @Size(min = 12, max = 72, message = "must be between 12 and 72 characters")
        String ownerPassword) {
}
