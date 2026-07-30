package com.dfs.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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

    private void createBucket(String name, boolean versioning) throws Exception {
        mockMvc.perform(put(BUCKETS + "/" + name)
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", versioning))))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> putBody(long size, String hash, Map<String, String> userMetadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("sizeBytes", size);
        body.put("contentHash", hash);
        body.put("contentType", "text/plain");
        body.put("userMetadata", userMetadata);
        return body;
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
                .andExpect(jsonPath("$.etag").value(HASH_A))
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
        body.put("contentHash", HASH_A);

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
                .andExpect(header().string("ETag", "\"" + HASH_A + "\""));

        mockMvc.perform(head(BUCKETS + "/readable/objects/a.txt").header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + HASH_A + "\""))
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
                .andExpect(jsonPath("$.etag").value(HASH_B));

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

        mockMvc.perform(put(path).header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(putBody(1, "not-a-valid-hash", Map.of()))))
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
