package com.dfs.upload.domain.placement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orders the configured blob nodes by preference for one chunk, using rendezvous
 * (highest-random-weight) hashing (ADR 11).
 *
 * <p>Every node is scored against the chunk's digest and the highest score is
 * the preferred node. The ranking is the whole fallback order: if the first node
 * cannot take the write, the second is the next choice, and so on. Because each
 * score depends only on the node and the digest, removing a node moves only the
 * chunks that preferred it, and every other chunk keeps its first choice.
 *
 * <p>The ranking is <em>advice for writes only</em>. Once a chunk is stored, the
 * node it actually went to is recorded and reads follow that record. So this
 * function is not a contract other services depend on: changing the scoring, or
 * the node set, changes where future chunks go and nothing about where existing
 * ones are found.
 */
public final class RendezvousPlacement {

    /**
     * Separates the node id from the digest in the scored input, so that no pair
     * of ids and digests concatenates to the same bytes as another pair. A zero
     * byte cannot appear in a node id or in a hex digest.
     */
    private static final byte SEPARATOR = 0;

    private final List<String> nodeIds;

    public RendezvousPlacement(Collection<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("At least one blob node must be configured");
        }
        Set<String> seen = new HashSet<>();
        for (String id : nodeIds) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("A blob node id must not be blank");
            }
            if (id.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("A blob node id must not contain a NUL character: " + id);
            }
            // Two entries with one id would be one node reached at two addresses,
            // and a chunk recorded against that id could be read from either.
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Blob node id configured twice: " + id);
            }
        }
        this.nodeIds = List.copyOf(nodeIds);
    }

    /** The configured nodes, in configuration order. */
    public List<String> nodeIds() {
        return nodeIds;
    }

    /**
     * Every configured node, most preferred first, for the chunk with this
     * digest.
     *
     * <p>The order does not depend on the order nodes were configured in. Ties
     * between scores (vanishingly rare with 64-bit scores) are broken by node id,
     * so the ranking is a pure function of the node set and the digest.
     */
    public List<String> rank(String digest) {
        if (digest == null || digest.isEmpty()) {
            throw new IllegalArgumentException("A chunk digest is required to place it");
        }
        Comparator<String> byScoreDescending = Comparator
                .comparing((String id) -> score(id, digest), Long::compareUnsigned)
                .reversed();
        return nodeIds.stream()
                .sorted(byScoreDescending.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * The first eight bytes of SHA-256(node id, separator, digest), read as an
     * unsigned 64-bit number.
     *
     * <p>A cryptographic hash rather than {@code String.hashCode}: the score must
     * be uniform across nodes for the load to spread evenly, and must be stable
     * across JVMs and releases so that restarting the orchestrator does not
     * reshuffle where new chunks prefer to go.
     */
    static long score(String nodeId, String digest) {
        MessageDigest sha256 = newSha256();
        sha256.update(nodeId.getBytes(StandardCharsets.UTF_8));
        sha256.update(SEPARATOR);
        sha256.update(digest.getBytes(StandardCharsets.UTF_8));
        byte[] hash = sha256.digest();

        long score = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            score = (score << 8) | (hash[i] & 0xFF);
        }
        return score;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
