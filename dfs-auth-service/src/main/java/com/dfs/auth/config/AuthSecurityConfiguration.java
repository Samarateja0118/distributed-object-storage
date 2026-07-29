package com.dfs.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.dfs.common.infrastructure.config.DatabaseConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;
import com.dfs.common.infrastructure.config.RedisConfiguration;
import com.dfs.common.security.HmacSigningKey;

import javax.crypto.SecretKey;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
@Import({DatabaseConfiguration.class, RedisConfiguration.class, OpenApiConfiguration.class})
public class AuthSecurityConfiguration {

    /**
     * Only the endpoints that mint credentials are open. Everything else derives
     * its tenant from the bearer token, so an authenticated caller cannot reach
     * another tenant's data by editing a path.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/token", "/api/v1/tenants").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        SecretKey signingKey = HmacSigningKey.deriveFrom(properties.jwt().signingSecret());
        return NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
