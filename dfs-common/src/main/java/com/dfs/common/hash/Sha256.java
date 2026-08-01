package com.dfs.common.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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

    /**
     * The composite digest naming a chunked object: SHA-256 over its ordered
     * chunk digests (ADR 9).
     *
     * <p>Once an object is many chunks, no single stream sees all of its bytes in
     * order, so there is no moment at which the whole-object hash is known —
     * computing one would mean reading every chunk back. This names the object by
     * its chunk list instead, which is in hand the moment the last chunk lands.
     *
     * <p>The digests are hashed as their decoded bytes, not as hex text. Hex is a
     * rendering, and hashing a rendering would make object identity depend on a
     * display choice; the 32 bytes a digest stands for are the digest itself.
     * Order is part of the identity — the same chunks in a different order are a
     * different object, which is what makes this a list rather than a set.
     *
     * <p>An object with no chunks, meaning a zero-byte object, hashes to SHA-256
     * of no input — the same value {@code hexOfContent(new byte[0])} returns.
     * That is a coincidence of the empty case, not a general property: for every
     * other object this is emphatically not the hash of the content.
     */
    public static String composite(List<String> chunkDigests) {
        MessageDigest digest = newDigest();
        for (String chunkDigest : chunkDigests) {
            if (!isValidHex(chunkDigest)) {
                throw new IllegalArgumentException("Not a lowercase hex SHA-256 digest: " + chunkDigest);
            }
            digest.update(HexFormat.of().parseHex(chunkDigest));
        }
        return hexOf(digest);
    }
}
