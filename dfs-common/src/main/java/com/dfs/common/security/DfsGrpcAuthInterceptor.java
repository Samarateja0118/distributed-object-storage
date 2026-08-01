package com.dfs.common.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Authenticates gRPC calls with the same decoder the HTTP resource servers use.
 *
 * <p>A {@code SecurityFilterChain} is a servlet filter chain and never sees a gRPC
 * call, so a service exposing both transports has to enforce its policy twice.
 * That is exactly the drift the shared token contract exists to prevent, so this
 * takes the {@link JwtDecoder} bean from {@link DfsResourceServerConfiguration}
 * rather than building one: one decoder, two entry points.
 *
 * <p>Every method is authenticated. There is no permit-list because gRPC here
 * carries only the internal data plane — liveness is served by actuator over
 * HTTP, so nothing on this transport needs to be reachable anonymously.
 */
public class DfsGrpcAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public DfsGrpcAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public <R, S> ServerCall.Listener<R> interceptCall(
            ServerCall<R, S> call, Metadata headers, ServerCallHandler<R, S> next) {

        String header = headers.get(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return reject(call, "Missing bearer token");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(BEARER.length()));
        } catch (JwtException e) {
            // The reason a token failed is not the caller's business: it tells an
            // attacker which half of a guess was right.
            return reject(call, "Invalid token");
        }

        return Contexts.interceptCall(Context.current().withValue(DfsGrpcContext.JWT, jwt), call, headers, next);
    }

    /**
     * Closing the call is not enough on its own — a client-streaming caller may
     * already be sending frames, so the interceptor still has to hand back a
     * listener. An empty one discards them.
     */
    private static <R, S> ServerCall.Listener<R> reject(ServerCall<R, S> call, String description) {
        call.close(Status.UNAUTHENTICATED.withDescription(description), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
