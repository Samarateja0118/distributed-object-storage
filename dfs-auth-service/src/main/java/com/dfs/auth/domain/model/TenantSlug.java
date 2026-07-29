package com.dfs.auth.domain.model;

import com.dfs.auth.domain.exception.InvalidTenantSlugException;

import java.util.regex.Pattern;

/**
 * URL-safe tenant identifier. Constrained like a bucket name so a slug can be
 * used as a DNS label if tenants ever get their own hostnames.
 */
public record TenantSlug(String value) {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 63;
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    public TenantSlug {
        if (value == null || value.isBlank()) {
            throw new InvalidTenantSlugException("Tenant slug must not be blank");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidTenantSlugException(
                    "Tenant slug must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (!VALID.matcher(value).matches()) {
            throw new InvalidTenantSlugException(
                    "Tenant slug must contain only lowercase letters, digits, and hyphens, "
                            + "and must start and end with a letter or digit");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
