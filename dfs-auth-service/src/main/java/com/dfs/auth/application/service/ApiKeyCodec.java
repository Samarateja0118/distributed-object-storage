package com.dfs.auth.application.service;

import com.dfs.auth.domain.exception.InvalidCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Encodes and parses the {@code dfs_<prefix>_<secret>} API key format.
 *
 * <p>The prefix is stored in clear so a key can be looked up in one indexed
 * read; the secret is what actually authenticates and is only ever persisted as
 * a hash.
 */
final class ApiKeyCodec {

    static final String PREFIX_MARKER = "dfs";
    private static final int PREFIX_BYTES = 6;
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hex, not base64url: the latter's alphabet includes the underscore used as
     * the segment separator, which would make keys ambiguous to parse.
     */
    private static final HexFormat HEX = HexFormat.of();

    private ApiKeyCodec() {
    }

    static GeneratedKey generate() {
        String prefix = randomToken(PREFIX_BYTES);
        String secret = randomToken(SECRET_BYTES);
        return new GeneratedKey(prefix, secret, "%s_%s_%s".formatted(PREFIX_MARKER, prefix, secret));
    }

    /**
     * Rejects anything that is not exactly three segments: a malformed key must
     * fail as a credential error, never as a parse error surfaced to the caller.
     */
    static ParsedKey parse(String plaintextKey) {
        if (plaintextKey == null) {
            throw new InvalidCredentialsException();
        }
        String[] segments = plaintextKey.trim().split("_");
        if (segments.length != 3 || !PREFIX_MARKER.equals(segments[0])
                || segments[1].isEmpty() || segments[2].isEmpty()) {
            throw new InvalidCredentialsException();
        }
        return new ParsedKey(segments[1], segments[2]);
    }

    /**
     * The cache is keyed on this digest rather than on the raw secret, so Redis
     * never holds material that could be replayed.
     */
    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    record GeneratedKey(String prefix, String secret, String plaintext) {
    }

    record ParsedKey(String prefix, String secret) {
    }
}
