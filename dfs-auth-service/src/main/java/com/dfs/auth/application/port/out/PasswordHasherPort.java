package com.dfs.auth.application.port.out;

/**
 * Outbound port for one-way credential hashing, keeping the concrete algorithm
 * out of the application layer so it can be rotated without touching services.
 */
public interface PasswordHasherPort {

    String hash(String plaintext);

    boolean matches(String plaintext, String hash);
}
