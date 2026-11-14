package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.domain.placement.RendezvousPlacement;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk placement across more than one blob node (ADR 11): chunks go to the node
 * their digest ranks first, fall back to the next node that answers, and are read
 * back from wherever they were recorded — never from where the ranking would put
 * them today.
 */
class ChunkPlacementIT extends AbstractUploadOrchestratorIT {

    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = Sha256.hexOfContent(CONTENT);

    @Autowired
    private RendezvousPlacement placement;

    private String preferred() {
        return placement.rank(DIGEST).getFirst();
    }

    private String secondChoice() {
        return placement.rank(DIGEST).get(1);
    }

    @Test
    @DisplayName("the configured node set is the one placement ranks over")
    void placementCoversEveryConfiguredNode() {
        assertThat(placement.nodeIds()).containsExactlyElementsOf(DownstreamStub.NODE_IDS);
    }

    @Test
    @DisplayName("chunks of one object spread across nodes, each recorded where it was written")
    void chunksSpreadAcrossNodes() throws Exception {
        int chunkSize = 64 * 1024;
        byte[] content = new byte[chunkSize * 12];
        new Random(11).nextBytes(content);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/spread.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(content))
                .andExpect(status().isCreated());

        var puts = DOWNSTREAM.blobCalls().stream().filter(call -> call.operation().equals("PutChunk")).toList();
        assertThat(puts).hasSize(12);
        // Each chunk went to its own first choice, and with twelve chunks on two
        // nodes both are used. The seed is fixed, so this is deterministic.
        assertThat(puts).allSatisfy(call ->
                assertThat(call.nodeId()).isEqualTo(placement.rank(call.digest()).getFirst()));
        assertThat(puts).extracting(DownstreamStub.BlobCall::nodeId)
                .containsAll(DownstreamStub.NODE_IDS);

        List<Map<String, Object>> chunks = registeredChunks();
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i)).containsEntry("nodeId", puts.get(i).nodeId());
        }

        byte[] downloaded = download("spread.bin");
        assertThat(downloaded).isEqualTo(content);

        // Every read went to the node its chunk was recorded on.
        var gets = DOWNSTREAM.blobCalls().stream().filter(call -> call.operation().equals("GetChunk")).toList();
        assertThat(gets).hasSize(12);
        for (int i = 0; i < gets.size(); i++) {
            assertThat(gets.get(i).nodeId()).isEqualTo(puts.get(i).nodeId());
        }
    }

    /**
     * The case ADR 11 is built around. The preferred node is down, so the chunk
     * goes to the next node in its ranking and is recorded there. When the
     * preferred node comes back, the read still goes to the node that holds the
     * bytes: it follows the record, not the ranking, so no repair is needed.
     */
    @Test
    @DisplayName("an unavailable node's chunks go to the next node, and are read from there after it recovers")
    void fallsBackWhenThePreferredNodeIsDown() throws Exception {
        DOWNSTREAM.failBlobNodeWith(preferred(), Status.UNAVAILABLE);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isCreated());

        assertThat(DOWNSTREAM.blobCalls())
                .extracting(DownstreamStub.BlobCall::nodeId)
                .containsExactly(preferred(), secondChoice());
        assertThat(DOWNSTREAM.hasBlobOn(secondChoice(), DIGEST)).isTrue();
        assertThat(DOWNSTREAM.hasBlobOn(preferred(), DIGEST)).isFalse();
        assertThat(registeredChunks().getFirst()).containsEntry("nodeId", secondChoice());

        DOWNSTREAM.failBlobNodeWith(preferred(), null);
        assertThat(download("fox.txt")).isEqualTo(CONTENT);

        var reads = DOWNSTREAM.blobCalls().stream().filter(call -> call.operation().equals("GetChunk")).toList();
        assertThat(reads).extracting(DownstreamStub.BlobCall::nodeId).containsExactly(secondChoice());
    }

    @Test
    @DisplayName("a node that answers and refuses is not skipped: the upload fails as it did with one node")
    void refusalIsNotRetriedOnAnotherNode() throws Exception {
        DOWNSTREAM.failBlobNodeWith(preferred(), Status.UNAUTHENTICATED);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isUnauthorized());

        assertThat(DOWNSTREAM.blobCalls()).extracting(DownstreamStub.BlobCall::nodeId).containsExactly(preferred());
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("when no node answers the upload fails as unreachable and registers nothing")
    void allNodesDownFailsTheUpload() throws Exception {
        DOWNSTREAM.failBlobWith(Status.UNAVAILABLE);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isGatewayTimeout());

        assertThat(DOWNSTREAM.blobCalls()).extracting(DownstreamStub.BlobCall::nodeId)
                .containsExactlyElementsOf(placement.rank(DIGEST));
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    /**
     * One copy per chunk in Phase 2, so there is nowhere else to read it from.
     * Falling back to another node on a read could only find a different copy
     * left by an earlier fallback write, which the record does not point at.
     */
    @Test
    @DisplayName("a read whose recorded node is down fails rather than trying another node")
    void readsDoNotFallBack() throws Exception {
        String holder = DownstreamStub.NODE_IDS.get(1);
        DOWNSTREAM.registerObjectOnNode("photos", "fox.txt", CONTENT, holder);
        DOWNSTREAM.failBlobNodeWith(holder, Status.UNAVAILABLE);

        mockMvc.perform(get("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isGatewayTimeout());

        assertThat(DOWNSTREAM.blobCalls()).extracting(DownstreamStub.BlobCall::nodeId).containsExactly(holder);
    }

    @Test
    @DisplayName("a chunk recorded on a node missing from the configuration is a server error, not a 404")
    void chunkOnUnconfiguredNodeIsAServerError() throws Exception {
        DOWNSTREAM.registerObjectOnNode("photos", "fox.txt", CONTENT, "blob-node-retired");

        mockMvc.perform(get("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Storage node not configured"));

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
    }

    @Test
    @DisplayName("a chunk recorded before Phase 2, on blob-node-0, is still readable")
    void legacyChunksOnTheFirstNodeStayReadable() throws Exception {
        DOWNSTREAM.registerObjectOnNode("photos", "old.txt", CONTENT, "blob-node-0");

        assertThat(download("old.txt")).isEqualTo(CONTENT);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> registeredChunks() throws Exception {
        Map<String, Object> registered =
                objectMapper.readValue(DOWNSTREAM.metadataRequests().getFirst().body(), Map.class);
        return (List<Map<String, Object>>) registered.get("chunks");
    }

    private byte[] download(String key) throws Exception {
        return mockMvc.perform(get("/api/v1/buckets/photos/objects/" + key)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }
}
