package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.application.service.ChunkPlacement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The properties ADR 11 relies on. Distribution and disruption are asserted over
 * a population of digests rather than a handful, because both are claims about
 * behaviour in aggregate — a single example can look fine under a placement rule
 * that is badly wrong.
 */
class ChunkPlacementTest {

    private static final List<String> THREE_NODES = List.of("blob-node-0", "blob-node-1", "blob-node-2");

    private static List<String> digests(int count) {
        List<String> digests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            digests.add(Sha256.hexOfContent(("chunk-" + i).getBytes(StandardCharsets.UTF_8)));
        }
        return digests;
    }

    private static Map<String, Integer> spread(ChunkPlacement placement, List<String> digests) {
        Map<String, Integer> counts = new HashMap<>();
        for (String digest : digests) {
            counts.merge(placement.rankFor(digest).getFirst(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void ranksEveryNodeExactlyOnce() {
        List<String> rank = new ChunkPlacement(THREE_NODES).rankFor(Sha256.hexOfContent("x".getBytes()));

        assertThat(rank).containsExactlyInAnyOrderElementsOf(THREE_NODES);
    }

    @Test
    void isDeterministic() {
        ChunkPlacement placement = new ChunkPlacement(THREE_NODES);
        String digest = Sha256.hexOfContent("stable".getBytes());

        assertThat(placement.rankFor(digest)).isEqualTo(placement.rankFor(digest));
    }

    /**
     * The node set is a set, not a list. If the order it was written in leaked
     * into the ranking, two services configured with the same nodes in a
     * different order would disagree about where a chunk belongs.
     */
    @Test
    void doesNotDependOnTheOrderTheNodesWereConfiguredIn() {
        ChunkPlacement asWritten = new ChunkPlacement(THREE_NODES);
        ChunkPlacement reversed = new ChunkPlacement(THREE_NODES.reversed());

        for (String digest : digests(200)) {
            assertThat(reversed.rankFor(digest)).isEqualTo(asWritten.rankFor(digest));
        }
    }

    /** The point of hashing the pair: chunks spread instead of piling onto one node. */
    @Test
    void spreadsChunksAcrossTheNodeSet() {
        Map<String, Integer> counts = spread(new ChunkPlacement(THREE_NODES), digests(3000));

        assertThat(counts.keySet()).containsExactlyInAnyOrderElementsOf(THREE_NODES);
        // A third each, give or take. Loose enough not to be flaky, tight enough
        // that a rule sending most chunks one way would fail.
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(800, 1200));
    }

    /**
     * Rendezvous hashing's reason for being: growing the cluster should move
     * about 1/N of the chunks, not rearrange all of them.
     */
    @Test
    void movesOnlyASmallShareOfChunksWhenANodeIsAdded() {
        List<String> digests = digests(3000);
        ChunkPlacement before = new ChunkPlacement(THREE_NODES);
        ChunkPlacement after = new ChunkPlacement(
                List.of("blob-node-0", "blob-node-1", "blob-node-2", "blob-node-3"));

        long moved = digests.stream()
                .filter(d -> !before.rankFor(d).getFirst().equals(after.rankFor(d).getFirst()))
                .count();

        // Expect ~1/4 to land on the new node; nothing should move between the
        // three that were already there.
        assertThat(moved).isBetween(600L, 900L);
        assertThat(digests.stream()
                .filter(d -> !before.rankFor(d).getFirst().equals(after.rankFor(d).getFirst()))
                .allMatch(d -> after.rankFor(d).getFirst().equals("blob-node-3")))
                .as("chunks that moved should have moved to the new node, not between existing ones")
                .isTrue();
    }

    /**
     * Removing a node must not disturb chunks that were not on it — those are
     * still readable, and their preference should not change underneath them.
     */
    @Test
    void leavesOtherNodesChunksAloneWhenANodeIsRemoved() {
        List<String> digests = digests(2000);
        ChunkPlacement before = new ChunkPlacement(THREE_NODES);
        ChunkPlacement after = new ChunkPlacement(List.of("blob-node-0", "blob-node-1"));

        for (String digest : digests) {
            String was = before.rankFor(digest).getFirst();
            if (!was.equals("blob-node-2")) {
                assertThat(after.rankFor(digest).getFirst()).isEqualTo(was);
            }
        }
    }

    @Test
    void refusesAnEmptyNodeSet() {
        assertThatThrownBy(() -> new ChunkPlacement(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
