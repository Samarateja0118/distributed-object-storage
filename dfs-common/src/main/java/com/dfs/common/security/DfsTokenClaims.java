package com.dfs.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Reads caller identity out of a validated token.
 *
 * <p>Services take the tenant from here and never from the request path, body,
 * or a header — that is what stops one tenant from addressing another's data.
 */
public final class DfsTokenClaims {

    private DfsTokenClaims() {
    }

    public static UUID tenantId(Jwt jwt) {
        String claim = jwt.getClaimAsString(DfsJwtClaims.TENANT_ID);
        if (claim == null) {
            throw new IllegalStateException("Token is missing the %s claim".formatted(DfsJwtClaims.TENANT_ID));
        }
        return UUID.fromString(claim);
    }

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static String role(Jwt jwt) {
        return jwt.getClaimAsString(DfsJwtClaims.ROLE);
    }
}
