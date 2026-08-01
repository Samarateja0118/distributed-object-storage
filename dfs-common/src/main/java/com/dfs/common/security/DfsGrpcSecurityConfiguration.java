package com.dfs.common.security;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Registers {@link DfsGrpcAuthInterceptor} on every gRPC server in the cluster,
 * so a new gRPC service is authenticated by existing wiring rather than by
 * remembering to add it.
 *
 * <p>The condition is on the class, not on the bean method. Both the annotation
 * above and the interceptor named as the return type below live in optional
 * dependencies, and a {@code @Bean} method's return type is loaded as soon as the
 * declaring class is introspected — before any method-level condition is
 * evaluated. Guarding the method would therefore break context refresh in every
 * service without gRPC on the classpath.
 */
@Configuration
@ConditionalOnClass({GrpcGlobalServerInterceptor.class, JwtDecoder.class})
public class DfsGrpcSecurityConfiguration {

    @Bean
    @GrpcGlobalServerInterceptor
    DfsGrpcAuthInterceptor dfsGrpcAuthInterceptor(JwtDecoder jwtDecoder) {
        return new DfsGrpcAuthInterceptor(jwtDecoder);
    }
}
