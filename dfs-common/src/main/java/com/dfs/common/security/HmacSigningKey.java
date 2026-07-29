package com.dfs.common.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives the HS256 signing key from the configured secret.
 *
 * <p>Issuer and validators must agree byte for byte, so the derivation lives
 * here rather than being reimplemented per service. Hashing the secret also
 * removes a sharp edge: HS256 requires a 256-bit key, and a configured
 * passphrase shorter than 32 characters would otherwise be rejected at startup.
 * Hashing preserves whatever entropy the secret has — it does not manufacture
 * any, so a weak secret stays weak.
 */
public final class HmacSigningKey {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigningKey() {
    }

    public static SecretKey deriveFrom(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret must be configured");
        }
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(material, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }
}
