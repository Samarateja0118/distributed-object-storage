package com.dfs.common.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The project's one definition of a content digest: SHA-256, rendered as
 * lowercase hex.
 *
 * <p>Shared because the digest is an identifier passed between services — the
 * orchestrator computes it, the blob node verifies and stores under it, and the
 * metadata service records it. If any of them disagreed on the encoding, the
 * same bytes would be filed under two different names and deduplication would
 * quietly stop working.
 */
public final class Sha256 {

    public static final String ALGORITHM = "SHA-256";

    /** Lowercase only: uppercase hex of the same digest would be a second name for one object. */
    private static final Pattern LOWERCASE_HEX = Pattern.compile("^[a-f0-9]{64}$");

    private Sha256() {
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    public static boolean isValidHex(String candidate) {
        return candidate != null && LOWERCASE_HEX.matcher(candidate).matches();
    }

    public static String hexOf(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    public static String hexOf(MessageDigest digest) {
        return hexOf(digest.digest());
    }

    public static String hexOfContent(byte[] content) {
        return hexOf(newDigest().digest(content));
    }
}
