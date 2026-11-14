package com.dfs.upload.domain.placement;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendezvousPlacementTest {

    private static final List<String> THREE_NODES = List.of("blob-node-0", "blob-node-1", "blob-node-2");

    /** Real chunk digests, so the inputs look like what the ranking is fed in production. */
    private static List<String> digests(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Sha256.hexOfContent(("chunk-" + i).getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    @Test
    @DisplayName("ranks every configured node exactly once")
    void rankingIsAPermutationOfTheNodeSet() {
        RendezvousPlacement placement = new RendezvousPlacement(THREE_NODES);

        for (String digest : digests(50)) {
            assertThat(placement.rank(digest)).containsExactlyInAnyOrderElementsOf(THREE_NODES);
        }
    }

    @Test
    @DisplayName("the ranking is a pure function of the node set and the digest")
    void rankingIsDeterministicAndIndependentOfConfigurationOrder() {
        RendezvousPlacement configured = new RendezvousPlacement(THREE_NODES);
        RendezvousPlacement reordered = new RendezvousPlacement(List.of("blob-node-2", "blob-node-0", "blob-node-1"));

        for (String digest : digests(200)) {
            List<String> ranking = configured.rank(digest);
            assertThat(configured.rank(digest)).isEqualTo(ranking);
            assertThat(reordered.rank(digest)).isEqualTo(ranking);
        }
    }

    /**
     * The property rendezvous hashing is chosen for: removing a node moves only
     * the chunks that preferred it. Every other chunk keeps its first choice, and
     * the moved ones go to their own second choice rather than all to one node.
     */
    @Test
    @DisplayName("removing a node changes the first choice only of chunks that preferred it")
    void removingANodeMovesOnlyItsOwnChunks() {
        RendezvousPlacement before = new RendezvousPlacement(THREE_NODES);
        RendezvousPlacement after = new RendezvousPlacement(List.of("blob-node-0", "blob-node-1"));

        for (String digest : digests(1_000)) {
            List<String> ranking = before.rank(digest);
            String expected = ranking.getFirst().equals("blob-node-2") ? ranking.get(1) : ranking.getFirst();
            assertThat(after.rank(digest).getFirst()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("first choices spread evenly across the nodes")
    void firstChoicesAreBalanced() {
        RendezvousPlacement placement = new RendezvousPlacement(THREE_NODES);
        Map<String, Integer> firstChoices = new HashMap<>();

        for (String digest : digests(3_000)) {
            firstChoices.merge(placement.rank(digest).getFirst(), 1, Integer::sum);
        }

        // An expected 1,000 each. The bounds are wide enough that a correct
        // implementation does not fail on noise (well over five standard
        // deviations), and tight enough to catch a ranking that favours a node.
        assertThat(firstChoices).hasSize(3);
        assertThat(firstChoices.values()).allSatisfy(count -> assertThat(count).isBetween(850, 1_150));
    }

    @Test
    @DisplayName("a single node is every chunk's only choice")
    void singleNodeTakesEverything() {
        RendezvousPlacement placement = new RendezvousPlacement(List.of("blob-node-0"));

        assertThat(placement.rank(digests(1).getFirst())).containsExactly("blob-node-0");
    }

    @Test
    @DisplayName("scores do not change between releases, since they decide where new chunks prefer to go")
    void scoreIsStable() {
        // Pinned rather than recomputed: a refactor that changed the scored bytes
        // would pass every other test here while silently reshuffling placement.
        // The values are SHA-256("<node id>\0<digest>")'s first eight bytes, read
        // big-endian as a signed long, computed independently of this code.
        String digest = Sha256.hexOfContent("pinned".getBytes(StandardCharsets.UTF_8));
        assertThat(RendezvousPlacement.score("blob-node-0", digest)).isEqualTo(8011102141807466840L);
        assertThat(RendezvousPlacement.score("blob-node-1", digest)).isEqualTo(-955088061666923866L);
    }

    @Test
    @DisplayName("rejects a node set that could not place a chunk unambiguously")
    void rejectsInvalidNodeSets() {
        assertThatThrownBy(() -> new RendezvousPlacement(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
        assertThatThrownBy(() -> new RendezvousPlacement(List.of("blob-node-0", "blob-node-0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> new RendezvousPlacement(List.of("blob-node-0", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("rejects a missing digest")
    void rejectsMissingDigest() {
        RendezvousPlacement placement = new RendezvousPlacement(THREE_NODES);

        assertThatThrownBy(() -> placement.rank("")).isInstanceOf(IllegalArgumentException.class);
    }
}
