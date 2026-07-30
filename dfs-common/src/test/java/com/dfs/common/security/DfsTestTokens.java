package com.dfs.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Signs tokens in tests exactly as the auth service does, so resource-server
 * tests exercise the real decoder, issuer check, and claim names.
 *
 * <p>Published from {@code dfs-common} as a test artifact rather than copied into
 * each service's tests: every service that validates a token needs this, and
 * copies would drift from the issuer the moment one of them changed.
 */
public final class DfsTestTokens {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final String issuer;
    private final JwtEncoder encoder;

    public DfsTestTokens(DfsJwtProperties properties) {
        this.issuer = properties.issuer();
        this.encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(HmacSigningKey.deriveFrom(properties.signingSecret())));
    }

    /** Ready-to-send {@code Authorization} header value for a tenant. */
    public String bearerFor(UUID tenantId) {
        return "Bearer " + tokenFor(tenantId, UUID.randomUUID(), "OWNER");
    }

    public String tokenFor(UUID tenantId, UUID userId, String role) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(DEFAULT_TTL))
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(DfsJwtClaims.TENANT_ID, tenantId.toString())
                .claim(DfsJwtClaims.ROLE, role)
                .build();
        return encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
