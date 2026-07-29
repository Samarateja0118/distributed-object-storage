package com.dfs.common.security;

/**
 * Claim names shared by the token issuer and every service that validates one.
 *
 * <p>These are duplicated nowhere else on purpose: a typo on either side would
 * present as an authenticated request with no tenant, which is far worse than a
 * compile error.
 */
public final class DfsJwtClaims {

    /** Tenant the token acts on behalf of; every data access is scoped by it. */
    public static final String TENANT_ID = "tenant_id";

    /** Human-readable identity, for logs and audit trails only. */
    public static final String EMAIL = "email";

    /** Coarse role, currently OWNER or MEMBER. */
    public static final String ROLE = "role";

    private DfsJwtClaims() {
    }
}
