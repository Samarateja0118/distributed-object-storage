package com.dfs.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;

/**
 * The one decoder every DFS resource server uses.
 *
 * <p>Shared rather than copied per service so the algorithm, the key derivation,
 * and the issuer check cannot drift apart: a service that validated tokens
 * slightly differently would either reject traffic the issuer considers valid or,
 * worse, accept a token the others reject.
 *
 * <p>Conditional on the resource-server starter, which {@code dfs-common}
 * declares optional — services with no HTTP surface to protect do not pay for it.
 */
@Configuration
@ConditionalOnClass(JwtDecoder.class)
public class DfsResourceServerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(DfsJwtProperties properties) {
        SecretKey signingKey = HmacSigningKey.deriveFrom(properties.signingSecret());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(validator(properties.issuer()));
        return decoder;
    }

    /**
     * Pins the issuer on top of the default expiry and not-before checks. A valid
     * signature alone is not enough: it only proves the token was minted by
     * something holding the secret, not by our auth service.
     */
    private static OAuth2TokenValidator<Jwt> validator(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("dfs.security.jwt.issuer must be configured");
        }
        return JwtValidators.createDefaultWithIssuer(issuer);
    }
}
