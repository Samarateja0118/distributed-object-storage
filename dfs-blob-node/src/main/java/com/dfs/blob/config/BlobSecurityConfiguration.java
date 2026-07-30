package com.dfs.blob.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class BlobSecurityConfiguration {

    /**
     * The data plane still requires a valid token even though it is internal.
     * Unauthenticated writes would let anyone fill the node's disk, and "it is
     * only reachable inside the cluster" is a network assumption this service
     * cannot verify for itself.
     *
     * <p>Note what this does and does not buy: it authenticates the caller, but a
     * blob has no owner to authorize against, since identical content from two
     * tenants is one file. Deletion therefore has no endpoint here at all — see
     * {@link com.dfs.blob.api.rest.BlobController}.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }
}
