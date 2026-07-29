package com.dfs.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The signing secret and issuer, configured identically for the service that
 * mints tokens and every service that validates them.
 *
 * <p>Deliberately one property rather than a per-service copy: two secrets that
 * are supposed to match will eventually not, and the failure mode is every
 * request rejected in production.
 */
@ConfigurationProperties(prefix = "dfs.security.jwt")
public record DfsJwtProperties(
        String issuer,
        String signingSecret
) {
}
