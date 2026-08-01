package com.dfs.common.security;

import io.grpc.Context;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Carries the validated token from the authenticating interceptor to the service
 * implementation handling the call.
 *
 * <p>The gRPC equivalent of reading the {@code Jwt} off the security context in a
 * controller. What is stored is the token the interceptor actually verified, so a
 * handler cannot end up trusting a different one than was checked.
 */
public final class DfsGrpcContext {

    static final Context.Key<Jwt> JWT = Context.key("dfs-jwt");

    private DfsGrpcContext() {
    }

    /**
     * The validated token for the call in progress.
     *
     * @throws IllegalStateException if no token is present, which means the
     *         authenticating interceptor did not run — a wiring bug rather than an
     *         unauthenticated caller, since the interceptor rejects those itself.
     */
    public static Jwt requireJwt() {
        Jwt jwt = JWT.get();
        if (jwt == null) {
            throw new IllegalStateException(
                    "No validated token on the gRPC context; DfsGrpcAuthInterceptor is not registered");
        }
        return jwt;
    }
}
