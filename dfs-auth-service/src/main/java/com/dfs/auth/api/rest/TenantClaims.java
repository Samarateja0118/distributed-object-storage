package com.dfs.auth.api.rest;

import com.dfs.common.security.DfsJwtClaims;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Reads the caller's identity out of the validated token. Nothing in the API
 * layer accepts a tenant id from the request itself, which is what keeps one
 * tenant from addressing another's data.
 */
final class TenantClaims {

    private TenantClaims() {
    }

    static UUID tenantId(Jwt jwt) {
        String claim = jwt.getClaimAsString(DfsJwtClaims.TENANT_ID);
        if (claim == null) {
            throw new IllegalStateException("Token is missing the %s claim".formatted(DfsJwtClaims.TENANT_ID));
        }
        return UUID.fromString(claim);
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
