package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectTransferApiIT extends AbstractUploadOrchestratorIT {

    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

    /** What the single chunk holding this content is filed under on the node. */
    private static final String CONTENT_DIGEST = Sha256.hexOfContent(CONTENT);

    /**
     * What the object is called. Not the digest of the content: an object is
     * named by the hash of its chunk digests (ADR 9), which for a one-chunk
     * object is still a hash of a hash rather than of the bytes.
     */
    private static final String OBJECT_DIGEST = Sha256.composite(List.of(CONTENT_DIGEST));

    @Test
    @DisplayName("stores the bytes under their digest, then registers the metadata pointing at it")
    void uploadStoresBytesThenRegistersMetadata() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CONTENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentHash").value(OBJECT_DIGEST))
                .andExpect(jsonPath("$.sizeBytes").value(CONTENT.length))
                .andExpect(jsonPath("$.key").value("fox.txt"));

        var blobCall = DOWNSTREAM.blobCalls().getFirst();
        assertThat(blobCall.operation()).isEqualTo("PutChunk");
        assertThat(blobCall.digest()).isEqualTo(CONTENT_DIGEST);
        assertThat(blobCall.content()).isEqualTo(CONTENT);

        var metadataRequest = DOWNSTREAM.metadataRequests().getFirst();
        assertThat(metadataRequest.method()).isEqualTo("PUT");
        assertThat(metadataRequest.path()).isEqualTo("/api/v1/buckets/photos/objects/fox.txt");

        Map<String, Object> registered = objectMapper.readValue(metadataRequest.body(), Map.class);
        assertThat(registered)
                .containsEntry("contentHash", OBJECT_DIGEST)
                .containsEntry("sizeBytes", CONTENT.length)
                .containsEntry("contentType", "text/plain");
    }

    @Test
    @DisplayName("the digest it registers is the digest of what it actually stored")
    void digestIsComputedFromTheContent() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isCreated());

        var blobCall = DOWNSTREAM.blobCalls().getFirst();
        assertThat(blobCall.digest()).isEqualTo(Sha256.hexOfContent(blobCall.content()));
    }

    @Test
    @DisplayName("forwards the caller's own token to both services, unchanged, on either transport")
    void forwardsCallerTokenToBothServices() throws Exception {
        String callerToken = bearer();

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, callerToken)
                        .content(CONTENT))
                .andExpect(status().isCreated());

        // gRPC metadata for the blob node, an HTTP header for the metadata
        // service; the same string reaches both.
        assertThat(DOWNSTREAM.blobCalls().getFirst().authorization()).isEqualTo(callerToken);
        assertThat(DOWNSTREAM.metadataRequests().getFirst().authorization()).isEqualTo(callerToken);
    }

    @Test
    @DisplayName("download reverses the upload and returns the original bytes")
    void downloadReturnsTheUploadedBytes() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CONTENT))
                .andExpect(status().isCreated());

        byte[] downloaded = mockMvc.perform(get("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + OBJECT_DIGEST + "\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(CONTENT);
    }

    /**
     * The point of ADR 8. The content spans several chunks, each stored under its
     * own digest, and the object is named by the hash of that list — so this
     * checks the boundaries, the order, and the fact that reassembly returns the
     * original bytes rather than a plausible-looking permutation of them.
     */
    @Test
    @DisplayName("an object larger than one chunk is split, stored, and reassembled in order")
    void largeObjectIsChunkedAndReassembled() throws Exception {
        int chunkSize = 64 * 1024;
        byte[] content = new byte[chunkSize * 3 + 1234];
        new java.util.Random(42).nextBytes(content);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/big.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(content.length));

        // Four chunks: three full and a remainder, in the order they appear.
        var puts = DOWNSTREAM.blobCalls().stream().filter(call -> call.operation().equals("PutChunk")).toList();
        assertThat(puts).hasSize(4);
        assertThat(puts.subList(0, 3)).allSatisfy(call -> assertThat(call.content()).hasSize(chunkSize));
        assertThat(puts.getLast().content()).hasSize(1234);

        Map<String, Object> registered =
                objectMapper.readValue(DOWNSTREAM.metadataRequests().getFirst().body(), Map.class);
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) registered.get("chunks");
        assertThat(chunks).hasSize(4);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i)).containsEntry("index", i);
            assertThat(chunks.get(i)).containsEntry("digest", puts.get(i).digest());
            // Which node a chunk lands on is placement's decision (ADR 11), so the
            // assertion is that the recorded node is the one that actually took
            // the bytes — not that it is any particular node.
            assertThat(chunks.get(i).get("nodeId")).isIn(DownstreamStub.NODE_IDS);
            assertThat(chunks.get(i)).containsEntry("nodeId", puts.get(i).nodeId());
        }
        assertThat(registered).containsEntry("contentHash",
                Sha256.composite(puts.stream().map(DownstreamStub.BlobCall::digest).toList()));

        byte[] downloaded = mockMvc.perform(get("/api/v1/buckets/photos/objects/big.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(content);
    }

    /**
     * Two objects sharing a leading chunk store it once. Fixed-size chunking
     * deduplicates only where content is identically aligned, and this is that
     * case — the shared part starts at the same offset in both.
     */
    @Test
    @DisplayName("a chunk shared by two objects is stored once")
    void identicalChunksAcrossObjectsAreStoredOnce() throws Exception {
        int chunkSize = 64 * 1024;
        byte[] shared = new byte[chunkSize];
        new java.util.Random(7).nextBytes(shared);

        byte[] first = new byte[chunkSize + 10];
        System.arraycopy(shared, 0, first, 0, chunkSize);
        byte[] second = new byte[chunkSize + 20];
        System.arraycopy(shared, 0, second, 0, chunkSize);

        for (var entry : Map.of("a.bin", first, "b.bin", second).entrySet()) {
            mockMvc.perform(put("/api/v1/buckets/photos/objects/" + entry.getKey())
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .content(entry.getValue()))
                    .andExpect(status().isCreated());
        }

        String sharedDigest = Sha256.hexOfContent(shared);
        assertThat(DOWNSTREAM.blobCalls())
                .filteredOn(call -> call.operation().equals("PutChunk") && call.digest().equals(sharedDigest))
                .hasSize(2);
        // Sent twice, held once: the digest is the name, so the second write
        // lands on the first one's bytes.
        assertThat(DOWNSTREAM.hasBlob(sharedDigest)).isTrue();
    }

    @Test
    @DisplayName("keys containing slashes survive the round trip")
    void nestedKeysRoundTrip() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/2026/07/holiday/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("2026/07/holiday/fox.txt"));

        assertThat(DOWNSTREAM.metadataRequests().getFirst().path())
                .isEqualTo("/api/v1/buckets/photos/objects/2026/07/holiday/fox.txt");

        byte[] downloaded = mockMvc.perform(get("/api/v1/buckets/photos/objects/2026/07/holiday/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("caller-supplied metadata headers reach the metadata service and come back on download")
    void userMetadataRoundTrips() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("X-Dfs-Meta-Author", "ada")
                        .content(CONTENT))
                .andExpect(status().isCreated());

        Map<String, Object> registered =
                objectMapper.readValue(DOWNSTREAM.metadataRequests().getFirst().body(), Map.class);
        assertThat((Map<String, String>) registered.get("userMetadata")).containsEntry("author", "ada");

        mockMvc.perform(get("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("x-dfs-meta-author", "ada"));
    }

    @Test
    @DisplayName("identical content uploaded twice is stored under one digest")
    void repeatedUploadIsContentAddressed() throws Exception {
        for (String key : new String[]{"first.txt", "second.txt"}) {
            mockMvc.perform(put("/api/v1/buckets/photos/objects/" + key)
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .content(CONTENT))
                    .andExpect(status().isCreated());
        }

        assertThat(DOWNSTREAM.blobCalls())
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.digest()).isEqualTo(CONTENT_DIGEST));
    }

    @Test
    @DisplayName("serves an object registered without a content type as raw bytes")
    void objectWithoutContentTypeIsDownloadable() throws Exception {
        DOWNSTREAM.registerObjectWithoutContentType("photos", "legacy.bin", CONTENT_DIGEST, CONTENT);

        byte[] downloaded = mockMvc.perform(get("/api/v1/buckets/photos/objects/legacy.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("rejects an unauthenticated upload before contacting anything downstream")
    void rejectsUploadWithoutToken() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt").content(CONTENT))
                .andExpect(status().isUnauthorized());

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    /**
     * The limit is enforced against the bytes actually read, so it can only trip
     * once they have been read — and by then the chunks that fit are already
     * stored. That is the streaming trade-off, not a leak: no metadata is written,
     * so the object never becomes visible to any client, and the stray chunks are
     * unreferenced bytes for GC to reclaim, which is the failure ADR 6 already
     * chose to accept.
     *
     * <p>Checking Content-Length first would usually avoid the writes, but it
     * cannot be the enforcement point: a client can understate it or omit it
     * entirely under chunked encoding.
     */
    @Test
    @DisplayName("rejects an object larger than the configured limit, registering nothing")
    void rejectsObjectOverTheSizeLimit() throws Exception {
        byte[] tooLarge = new byte[(int) (1024 * 1024) + 1];

        mockMvc.perform(put("/api/v1/buckets/photos/objects/big.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(tooLarge))
                .andExpect(status().isPayloadTooLarge());

        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();

        // Nothing is readable, which is the property that matters.
        mockMvc.perform(get("/api/v1/buckets/photos/objects/big.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }
}
