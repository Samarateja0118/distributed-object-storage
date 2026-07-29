package com.dfs.auth.config;

import com.dfs.common.security.HmacSigningKey;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.time.Clock;

@Configuration
public class AuthInfrastructureConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        SecretKey signingKey = HmacSigningKey.deriveFrom(properties.jwt().signingSecret());
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }
}
