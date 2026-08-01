package com.dfs.metadata;

import com.dfs.common.hash.Sha256;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectMetadataApiIT extends AbstractMetadataIT {

    private static final String BUCKETS = "/api/v1/buckets";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final String NODE = "blob-node-0";

    private void createBucket(String name, boolean versioning) throws Exception {
        mockMvc.perform(put(BUCKETS + "/" + name)
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", versioning))))
                .andExpect(status().isCreated());
    }

    /**
     * An object of one chunk covering all of its bytes. The content hash is
     * derived rather than passed in, because the service now rejects a hash that
     * is not the composite of the chunk list (ADR 9) — a test that supplied both
     * independently would be asserting against an object the API will not accept.
     */
    private static Map<String, Object> putBody(long size, String chunkDigest, Map<String, String> userMetadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", size);
        body.put("contentHash", compositeOf(chunkDigest));
        body.put("contentType", "text/plain");
        body.put("chunks", List.of(chunk(0, chunkDigest, size)));
        body.put("userMetadata", userMetadata);
        return body;
    }

    private static Map<String, Object> chunk(int index, String digest, long sizeBytes) {
        return Map.of("index", index, "digest", digest, "sizeBytes", sizeBytes, "nodeId", NODE);
    }

    private static String compositeOf(String... chunkDigests) {
        return Sha256.composite(List.of(chunkDigests));
    }

    @Test
    void createsObjectMetadata() throws Exception {
        createBucket("docs", false);

        mockMvc.perform(put(BUCKETS + "/docs/objects/notes.txt")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(putBody(2048, HASH_A, Map.of("author", "sam")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("notes.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(2048))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.etag").value(compositeOf(HASH_A)))
                .andExpect(jsonPath("$.latest").value(true))
                .andExpect(jsonPath("$.userMetadata.author").value("sam"))
                .andExpect(header().exists("X-Object-Version-Id"));
    }

    @Test
    void supportsNestedKeysContainingSlashes() throws Exception {
        createBucket("nested", false);
        String key = "2026/07/quarterly-report.pdf";

        mockMvc.perform(put(BUCKETS + "/nested/objects/" + key)
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(putBody(10, HASH_A, Map.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(key));

        mockMvc.perform(get(BUCKETS + "/nested/objects/" + key).header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(key));
    }

    @Test
    void defaultsContentTypeWhenAbsent() throws Exception {
        createBucket("defaults", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 1);
        body.put("contentHash", compositeOf(HASH_A));
        body.put("chunks", List.of(chunk(0, HASH_A, 1)));

        mockMvc.perform(put(BUCKETS + "/defaults/objects/blob.bin")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/octet-stream"))
                .andExpect(jsonPath("$.userMetadata").isEmpty());
    }

    @Test
    void getsAndHeadsObject() throws Exception {
        createBucket("readable", false);
        mockMvc.perform(put(BUCKETS + "/readable/objects/a.txt")
                .header("Authorization", bearer(tenantId))
                .contentType("application/json")
                .content(json(putBody(512, HASH_A, Map.of()))));

        mockMvc.perform(get(BUCKETS + "/readable/objects/a.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + compositeOf(HASH_A) + "\""));

        mockMvc.perform(head(BUCKETS + "/readable/objects/a.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + compositeOf(HASH_A) + "\""))
                .andExpect(header().exists("X-Object-Version-Id"));
    }

    @Test
    void overwriteReplacesInPlaceWhenVersioningDisabled() throws Exception {
        createBucket("flat", false);
        String path = BUCKETS + "/flat/objects/file.txt";

        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                .contentType("application/json").content(json(putBody(100, HASH_A, Map.of()))));
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(putBody(200, HASH_B, Map.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(200))
                .andExpect(jsonPath("$.etag").value(compositeOf(HASH_B)));

        mockMvc.perform(get(BUCKETS + "/flat/object-versions/file.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void overwriteCreatesNewVersionWhenVersioningEnabled() throws Exception {
        createBucket("versioned", true);
        String path = BUCKETS + "/versioned/objects/file.txt";

        String first = mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(putBody(100, HASH_A, Map.of()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(putBody(200, HASH_B, Map.of()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstNode = objectMapper.readTree(first);
        JsonNode secondNode = objectMapper.readTree(second);
        assertThat(firstNode.get("versionId").asText()).isNotEqualTo(secondNode.get("versionId").asText());

        mockMvc.perform(get(path).header("Authorization", bearer(tenantId)))
                .andExpect(jsonPath("$.sizeBytes").value(200));

        mockMvc.perform(get(BUCKETS + "/versioned/object-versions/file.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listsObjectsFilteredByPrefix() throws Exception {
        createBucket("listing", false);
        for (String key : new String[]{"img/a.png", "img/b.png", "doc/c.txt"}) {
            mockMvc.perform(put(BUCKETS + "/listing/objects/" + key)
                    .header("Authorization", bearer(tenantId))
                    .contentType("application/json")
                    .content(json(putBody(1, HASH_A, Map.of()))));
        }

        mockMvc.perform(get(BUCKETS + "/listing/objects").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get(BUCKETS + "/listing/objects")
                        .header("Authorization", bearer(tenantId))
                        .param("prefix", "img/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].key").value("img/a.png"));
    }

    @Test
    void paginatesObjectListing() throws Exception {
        createBucket("paged", false);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put(BUCKETS + "/paged/objects/key-" + i)
                    .header("Authorization", bearer(tenantId))
                    .contentType("application/json")
                    .content(json(putBody(1, HASH_A, Map.of()))));
        }

        mockMvc.perform(get(BUCKETS + "/paged/objects")
                        .header("Authorization", bearer(tenantId))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].key").value("key-2"));
    }

    @Test
    void updatesUserMetadata() throws Exception {
        createBucket("taggable", false);
        mockMvc.perform(put(BUCKETS + "/taggable/objects/a.txt")
                .header("Authorization", bearer(tenantId))
                .contentType("application/json")
                .content(json(putBody(1, HASH_A, Map.of("stage", "draft")))));

        mockMvc.perform(patch(BUCKETS + "/taggable/objects/a.txt")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("userMetadata", Map.of("stage", "final", "owner", "sam")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMetadata.stage").value("final"))
                .andExpect(jsonPath("$.userMetadata.owner").value("sam"));

        mockMvc.perform(get(BUCKETS + "/taggable/objects/a.txt").header("Authorization", bearer(tenantId)))
                .andExpect(jsonPath("$.userMetadata.stage").value("final"));
    }

    @Test
    void deletesObjectAndAllowsRecreate() throws Exception {
        createBucket("deletable", false);
        String path = BUCKETS + "/deletable/objects/gone.txt";
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                .contentType("application/json").content(json(putBody(1, HASH_A, Map.of()))));

        mockMvc.perform(delete(path).header("Authorization", bearer(tenantId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path).header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(putBody(5, HASH_B, Map.of()))))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteRemovesEveryVersion() throws Exception {
        createBucket("multi", true);
        String path = BUCKETS + "/multi/objects/v.txt";
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                .contentType("application/json").content(json(putBody(1, HASH_A, Map.of()))));
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                .contentType("application/json").content(json(putBody(2, HASH_B, Map.of()))));

        mockMvc.perform(delete(path).header("Authorization", bearer(tenantId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BUCKETS + "/multi/object-versions/v.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingObjectAndBucket() throws Exception {
        createBucket("present", false);

        mockMvc.perform(get(BUCKETS + "/present/objects/absent.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));

        mockMvc.perform(get(BUCKETS + "/no-such-bucket/objects/a.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(BUCKETS + "/present/objects/absent.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        createBucket("strict", false);
        String path = BUCKETS + "/strict/objects/bad.txt";

        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(putBody(-1, HASH_A, Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sizeBytes").isNotEmpty());

        Map<String, Object> badHash = new HashMap<>();
        badHash.put("sizeBytes", 1);
        badHash.put("contentHash", "not-a-valid-hash");
        badHash.put("chunks", List.of(chunk(0, HASH_A, 1)));
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(badHash)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.contentHash").isNotEmpty());

        Map<String, Object> missingHash = new HashMap<>();
        missingHash.put("sizeBytes", 1);
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(missingHash)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsTheChunkListInOrder() throws Exception {
        createBucket("chunked", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 30);
        body.put("contentHash", compositeOf(HASH_A, HASH_B, HASH_C));
        body.put("chunks", List.of(chunk(0, HASH_A, 10), chunk(1, HASH_B, 10), chunk(2, HASH_C, 10)));

        mockMvc.perform(put(BUCKETS + "/chunked/objects/big.bin").header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BUCKETS + "/chunked/object-chunks/big.bin").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks.length()").value(3))
                .andExpect(jsonPath("$.chunks[0].digest").value(HASH_A))
                .andExpect(jsonPath("$.chunks[1].digest").value(HASH_B))
                .andExpect(jsonPath("$.chunks[2].digest").value(HASH_C))
                .andExpect(jsonPath("$.chunks[2].index").value(2))
                .andExpect(jsonPath("$.chunks[0].sizeBytes").value(10))
                .andExpect(jsonPath("$.chunks[0].nodeId").value(NODE));
    }

    /** A zero-byte object has no bytes to chunk, so it has no chunks. */
    @Test
    void storesAnObjectWithNoChunks() throws Exception {
        createBucket("empty", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 0);
        body.put("contentHash", compositeOf());
        body.put("chunks", List.of());

        mockMvc.perform(put(BUCKETS + "/empty/objects/nothing.txt").header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(0));

        mockMvc.perform(get(BUCKETS + "/empty/object-chunks/nothing.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks.length()").value(0));
    }

    /**
     * The check that keeps the two halves honest: a chunk list that does not
     * hash to the declared name would file an object under a digest that
     * describes different content, and nothing downstream recomputes it.
     */
    @Test
    void rejectsAChunkListThatDoesNotMatchTheContentHash() throws Exception {
        createBucket("verified", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 20);
        body.put("contentHash", compositeOf(HASH_A, HASH_B));
        // Same length, different content: the digest is what catches this.
        body.put("chunks", List.of(chunk(0, HASH_A, 10), chunk(1, HASH_C, 10)));

        mockMvc.perform(put(BUCKETS + "/verified/objects/wrong.bin").header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsChunkSizesThatDoNotAddUpToTheDeclaredSize() throws Exception {
        createBucket("sized", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 999);
        body.put("contentHash", compositeOf(HASH_A, HASH_B));
        body.put("chunks", List.of(chunk(0, HASH_A, 10), chunk(1, HASH_B, 10)));

        mockMvc.perform(put(BUCKETS + "/sized/objects/short.bin").header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest());
    }

    /**
     * A gap would be a hole in the content that nothing else detects: the
     * download would simply be short, and the composite digest would agree with
     * the truncated list.
     */
    @Test
    void rejectsChunkIndexesWithAGap() throws Exception {
        createBucket("gapped", false);
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", 20);
        body.put("contentHash", compositeOf(HASH_A, HASH_B));
        body.put("chunks", List.of(chunk(0, HASH_A, 10), chunk(2, HASH_B, 10)));

        mockMvc.perform(put(BUCKETS + "/gapped/objects/holey.bin").header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Overwriting with fewer chunks must not leave the tail of the previous list
     * behind, which would append the old object's trailing bytes to the new one.
     */
    @Test
    void overwriteReplacesTheWholeChunkList() throws Exception {
        createBucket("replaced", false);
        String path = BUCKETS + "/replaced/objects/file.bin";

        Map<String, Object> threeChunks = new HashMap<>();
        threeChunks.put("sizeBytes", 30);
        threeChunks.put("contentHash", compositeOf(HASH_A, HASH_B, HASH_C));
        threeChunks.put("chunks", List.of(chunk(0, HASH_A, 10), chunk(1, HASH_B, 10), chunk(2, HASH_C, 10)));
        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(threeChunks)))
                .andExpect(status().isCreated());

        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json").content(json(putBody(5, HASH_B, Map.of()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BUCKETS + "/replaced/object-chunks/file.bin").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks.length()").value(1))
                .andExpect(jsonPath("$.chunks[0].digest").value(HASH_B));
    }

    @Test
    void chunkListsAreScopedToTheirTenant() throws Exception {
        createBucket("private", false);
        mockMvc.perform(put(BUCKETS + "/private/objects/secret.bin").header("Authorization", bearer(tenantId))
                .contentType("application/json").content(json(putBody(10, HASH_A, Map.of()))));

        mockMvc.perform(get(BUCKETS + "/private/object-chunks/secret.bin")
                        .header("Authorization", bearer(java.util.UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void objectsAreScopedToTheirTenant() throws Exception {
        createBucket("scoped", false);
        mockMvc.perform(put(BUCKETS + "/scoped/objects/secret.txt")
                .header("Authorization", bearer(tenantId))
                .contentType("application/json")
                .content(json(putBody(1, HASH_A, Map.of()))));

        mockMvc.perform(get(BUCKETS + "/scoped/objects/secret.txt")
                        .header("Authorization", bearer(java.util.UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
