package com.dfs.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 11 against two real nodes: placement chooses, the choice is recorded, and
 * reads follow the record rather than recomputing it.
 */
@SuppressWarnings("unchecked")
class ChunkPlacementIT extends AbstractUploadOrchestratorIT {

    private static final String KEY = "/api/v1/buckets/photos/objects/placed.bin";

    private List<Map<String, Object>> registeredChunks() throws Exception {
        Map<String, Object> registered = new ObjectMapper()
                .readValue(DOWNSTREAM.metadataRequests().getFirst().body(), Map.class);
        return (List<Map<String, Object>>) registered.get("chunks");
    }

    private byte[] upload(String path, byte[] content) throws Exception {
        mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, bearer()).content(content))
                .andExpect(status().isCreated());
        return content;
    }

    /**
     * The chunk is recorded against whichever node actually took it. Everything
     * else in this file depends on that being true.
     */
    @Test
    void recordsTheNodeThatTookTheChunk() throws Exception {
        byte[] content = upload(KEY, "placed once".getBytes());

        List<Map<String, Object>> chunks = registeredChunks();
        assertThat(chunks).hasSize(1);
        String recorded = (String) chunks.getFirst().get("nodeId");
        String digest = (String) chunks.getFirst().get("digest");

        assertThat(DOWNSTREAM.nodeHolding(digest)).contains(recorded);
        assertThat(DOWNSTREAM.hasBlob(recorded, digest)).isTrue();
        assertThat(content).isNotEmpty();
    }

    /**
     * A node being down costs the time to fail over and nothing else. The write
     * succeeds, and — the part that matters — it is recorded against the node
     * that took it rather than the one placement preferred.
     */
    @Test
    void failsOverToTheNextNodeAndRecordsItThere() throws Exception {
        byte[] content = "written while a node is down".getBytes();
        // Take down whichever node this chunk would have preferred.
        String preferred = preferredNodeFor(content);
        DOWNSTREAM.failNode(preferred, Status.UNAVAILABLE);

        upload(KEY, content);

        List<Map<String, Object>> chunks = registeredChunks();
        String recorded = (String) chunks.getFirst().get("nodeId");
        String digest = (String) chunks.getFirst().get("digest");

        assertThat(recorded).isNotEqualTo(preferred);
        assertThat(DOWNSTREAM.hasBlob(recorded, digest)).isTrue();
        assertThat(DOWNSTREAM.hasBlob(preferred, digest)).isFalse();
    }

    /**
     * The claim ADR 11 is built on: a chunk written to a fallback node is
     * indistinguishable from a normal one on read, because the read follows the
     * record. If the download recomputed placement it would ask the preferred
     * node, which does not have the bytes.
     */
    @Test
    void downloadsAChunkFromTheNodeItWasRecordedOnRatherThanThePreferredOne() throws Exception {
        byte[] content = "read me from where I actually am".getBytes();
        String preferred = preferredNodeFor(content);
        DOWNSTREAM.failNode(preferred, Status.UNAVAILABLE);
        upload(KEY, content);

        String recorded = (String) registeredChunks().getFirst().get("nodeId");
        // Bring the preferred node back. A download that recomputed the ranking
        // would now succeed in asking the wrong node and get a NOT_FOUND.
        DOWNSTREAM.failNode(preferred, null);

        byte[] downloaded = mockMvc.perform(get(KEY).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(content);
        assertThat(DOWNSTREAM.blobCalls())
                .filteredOn(call -> call.operation().equals("GetChunk"))
                .allSatisfy(call -> assertThat(call.nodeId()).isEqualTo(recorded));
    }

    /** With the whole cluster unreachable there is nowhere left to walk to. */
    @Test
    void failsTheUploadWhenNoNodeAnswers() throws Exception {
        DOWNSTREAM.failBlobWith(Status.UNAVAILABLE);

        mockMvc.perform(put(KEY).header(HttpHeaders.AUTHORIZATION, bearer()).content("nowhere to go".getBytes()))
                // 504, not 503: the existing handler treats an unreachable
                // downstream as a gateway timeout, and placement changes nothing
                // about how that surfaces to a client.
                .andExpect(status().isGatewayTimeout());

        assertThat(DOWNSTREAM.metadataRequests())
                .as("nothing should be registered when the content was never stored")
                .isEmpty();
    }

    /**
     * Uploads a probe object to learn which node placement prefers for this
     * content, then clears the record of it. Asking the system rather than
     * reimplementing the hash keeps this test from asserting against its own
     * copy of the rule.
     */
    private String preferredNodeFor(byte[] content) throws Exception {
        upload("/api/v1/buckets/photos/objects/probe.bin", content);
        String preferred = (String) registeredChunks().getFirst().get("nodeId");
        DOWNSTREAM.reset();
        return preferred;
    }
}
