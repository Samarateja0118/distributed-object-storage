package com.dfs.auth.infrastructure.adapter.out.token;

import com.dfs.auth.application.port.out.TokenIssuerPort;
import com.dfs.auth.config.AuthProperties;
import com.dfs.auth.domain.model.AccessToken;
import com.dfs.auth.domain.model.Principal;
import com.dfs.common.security.DfsJwtClaims;
import com.dfs.common.security.DfsJwtProperties;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Mints HS256 access tokens. The subject is the user id; the tenant travels in
 * its own claim because every downstream authorization decision is scoped by it.
 */
@Component
class NimbusTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    NimbusTokenIssuerAdapter(JwtEncoder jwtEncoder,
                             DfsJwtProperties jwtProperties,
                             AuthProperties authProperties,
                             Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = jwtProperties.issuer();
        this.ttl = authProperties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    @Override
    public AccessToken issue(Principal principal) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(principal.userId().toString())
                .id(UUID.randomUUID().toString())
                .claim(DfsJwtClaims.TENANT_ID, principal.tenantId().toString())
                .claim(DfsJwtClaims.EMAIL, principal.email().value())
                .claim(DfsJwtClaims.ROLE, principal.role().name())
                .build();

        String value = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
