package com.dfs.metadata;

import com.dfs.common.hash.Sha256;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multipart upload sessions end to end against PostgreSQL (ADR 12).
 *
 * <p>Sizes here are declared rather than real: the metadata service records
 * chunk lists, never bytes, so a part of 5 MiB costs a number, not an
 * allocation. That keeps the minimum part size at its production value.
 */
class MultipartUploadApiIT extends AbstractMetadataIT {

    private static final String BUCKETS = "/api/v1/buckets";
    private static final long MIN_PART = 5L * 1024 * 1024;

    @Autowired
    private JdbcTemplate jdbc;

    private static String digest(char c) {
        return String.valueOf(c).repeat(64);
    }

    private void createBucket(UUID tenant, String name, boolean versioning) throws Exception {
        mockMvc.perform(put(BUCKETS + "/" + name)
                        .header("Authorization", bearer(tenant))
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", versioning))))
                .andExpect(status().isCreated());
    }

    private UUID initiate(String bucket, String key) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("key", key);
        body.put("contentType", "video/mp4");
        body.put("userMetadata", Map.of("camera", "a7"));
        String response = mockMvc.perform(post(BUCKETS + "/" + bucket + "/multipart-uploads")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("uploadId").asText());
    }

    /** A part of one chunk per size, digests running from {@code first}. */
    private static Map<String, Object> partBody(char first, long... sizes) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        List<String> digests = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < sizes.length; i++) {
            String digest = digest((char) (first + i));
            digests.add(digest);
            total += sizes[i];
            chunks.add(Map.of("index", i, "digest", digest, "sizeBytes", sizes[i], "nodeId", "blob-node-" + (i % 2)));
        }
        return Map.of("sizeBytes", total, "contentHash", Sha256.composite(digests), "chunks", chunks);
    }

    private static String etagOf(Map<String, Object> partBody) {
        return (String) partBody.get("contentHash");
    }

    private String partPath(String bucket, UUID uploadId, int partNumber) {
        return BUCKETS + "/" + bucket + "/multipart-uploads/" + uploadId + "/parts/" + partNumber;
    }

    private void putPart(String bucket, UUID uploadId, int partNumber, Map<String, Object> body) throws Exception {
        mockMvc.perform(put(partPath(bucket, uploadId, partNumber))
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(partNumber))
                .andExpect(jsonPath("$.etag").value(etagOf(body)))
                .andExpect(header().string("ETag", "\"" + etagOf(body) + "\""));
    }

    private static Map<String, Object> completion(Object... numberThenEtag) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (int i = 0; i < numberThenEtag.length; i += 2) {
            parts.add(Map.of("partNumber", numberThenEtag[i], "etag", numberThenEtag[i + 1]));
        }
        return Map.of("parts", parts);
    }

    private org.springframework.test.web.servlet.ResultActions complete(
            String bucket, UUID uploadId, UUID tenant, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(BUCKETS + "/" + bucket + "/multipart-uploads/" + uploadId + "/complete")
                .header("Authorization", bearer(tenant))
                .contentType("application/json")
                .content(json(body)));
    }

    private int sessionRows(UUID uploadId) {
        return jdbc.queryForObject("SELECT count(*) FROM multipart_uploads WHERE id = ?", Integer.class, uploadId);
    }

    private int stagedChunkRows(UUID uploadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM multipart_upload_chunks WHERE upload_id = ?", Integer.class, uploadId);
    }

    @Test
    @DisplayName("parts are staged, then completed into one object whose chunk list is theirs end to end")
    void completesAnUploadIntoAnObject() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "videos/clip.mp4");

        Map<String, Object> first = partBody('a', MIN_PART - 100, 100);
        Map<String, Object> second = partBody('c', 42);
        putPart("media", uploadId, 1, first);
        putPart("media", uploadId, 2, second);

        String objectHash = Sha256.composite(List.of(digest('a'), digest('b'), digest('c')));
        complete("media", uploadId, tenantId, completion(1, etagOf(first), 2, etagOf(second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("videos/clip.mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(MIN_PART + 42))
                .andExpect(jsonPath("$.contentHash").value(objectHash))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.userMetadata.camera").value("a7"))
                .andExpect(header().string("ETag", "\"" + objectHash + "\""))
                .andExpect(header().exists("X-Object-Version-Id"));

        // The object's chunk list is the parts' lists end to end, renumbered,
        // with each chunk's recorded node carried across unchanged (ADR 11).
        String chunks = mockMvc.perform(get(BUCKETS + "/media/object-chunks/videos/clip.mp4")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(chunks).get("chunks");
        assertThat(list).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(list.get(i).get("index").asInt()).isEqualTo(i);
            assertThat(list.get(i).get("digest").asText()).isEqualTo(digest((char) ('a' + i)));
        }
        assertThat(list.get(1).get("nodeId").asText()).isEqualTo("blob-node-1");

        // The session and its staged rows went in the same transaction.
        assertThat(sessionRows(uploadId)).isZero();
        assertThat(stagedChunkRows(uploadId)).isZero();
        mockMvc.perform(get(BUCKETS + "/media/multipart-uploads/" + uploadId + "/parts")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lists uploaded parts in order with the etags a client completes with")
    void listsParts() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> third = partBody('e', 7);
        Map<String, Object> first = partBody('a', MIN_PART);
        putPart("media", uploadId, 3, third);
        putPart("media", uploadId, 1, first);

        mockMvc.perform(get(BUCKETS + "/media/multipart-uploads/" + uploadId + "/parts")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.key").value("clip.mp4"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andExpect(jsonPath("$.parts[0].partNumber").value(1))
                .andExpect(jsonPath("$.parts[0].etag").value(etagOf(first)))
                .andExpect(jsonPath("$.parts[0].sizeBytes").value(MIN_PART))
                .andExpect(jsonPath("$.parts[1].partNumber").value(3))
                .andExpect(jsonPath("$.parts[1].etag").value(etagOf(third)));
    }

    @Test
    @DisplayName("re-uploading a part replaces it, and completing with the old etag is refused")
    void reuploadReplacesAPart() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> original = partBody('a', 10, 20);
        Map<String, Object> replacement = partBody('d', 5);
        putPart("media", uploadId, 1, original);
        putPart("media", uploadId, 1, replacement);

        // The shorter replacement left none of the original's chunks behind.
        assertThat(stagedChunkRows(uploadId)).isEqualTo(1);

        complete("media", uploadId, tenantId, completion(1, etagOf(original)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid part"));

        complete("media", uploadId, tenantId, completion(1, etagOf(replacement)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(5));
    }

    @Test
    @DisplayName("a part below the minimum size is refused unless it is last, and the session stays open")
    void enforcesMinimumPartSize() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> tooSmall = partBody('a', MIN_PART - 1);
        Map<String, Object> last = partBody('b', 1);
        putPart("media", uploadId, 1, tooSmall);
        putPart("media", uploadId, 2, last);

        complete("media", uploadId, tenantId, completion(1, etagOf(tooSmall), 2, etagOf(last)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid part"));

        // A failed completion changes nothing: the client can fix the part.
        assertThat(sessionRows(uploadId)).isOne();
        Map<String, Object> bigEnough = partBody('c', MIN_PART);
        putPart("media", uploadId, 1, bigEnough);
        complete("media", uploadId, tenantId, completion(1, etagOf(bigEnough), 2, etagOf(last)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("parts left out of the completion are not part of the object")
    void unlistedPartsAreLeftOut() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> first = partBody('a', MIN_PART);
        Map<String, Object> unwanted = partBody('b', MIN_PART);
        Map<String, Object> fifth = partBody('c', 3);
        putPart("media", uploadId, 1, first);
        putPart("media", uploadId, 2, unwanted);
        putPart("media", uploadId, 5, fifth);

        complete("media", uploadId, tenantId, completion(1, etagOf(first), 5, etagOf(fifth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(MIN_PART + 3))
                .andExpect(jsonPath("$.contentHash").value(Sha256.composite(List.of(digest('a'), digest('c')))));
    }

    @Test
    @DisplayName("aborting ends the session and deletes its staged rows")
    void abortDeletesTheSession() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> part = partBody('a', 10);
        putPart("media", uploadId, 1, part);

        mockMvc.perform(delete(BUCKETS + "/media/multipart-uploads/" + uploadId)
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isNoContent());

        assertThat(sessionRows(uploadId)).isZero();
        assertThat(stagedChunkRows(uploadId)).isZero();
        complete("media", uploadId, tenantId, completion(1, etagOf(part)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BUCKETS + "/media/multipart-uploads/" + uploadId)
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a completed upload cannot be completed again or given more parts")
    void completedUploadIsGone() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> part = partBody('a', 10);
        putPart("media", uploadId, 1, part);
        complete("media", uploadId, tenantId, completion(1, etagOf(part))).andExpect(status().isCreated());

        complete("media", uploadId, tenantId, completion(1, etagOf(part))).andExpect(status().isNotFound());
        mockMvc.perform(put(partPath("media", uploadId, 2))
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(partBody('b', 10))))
                .andExpect(status().isNotFound());
    }

    /**
     * An upload id is a name, not a capability (ADR 12). Another tenant holding
     * the id — even with a bucket of the same name — reaches nothing, and the
     * owner's session is untouched by the attempts.
     */
    @Test
    @DisplayName("another tenant cannot use an upload id, even with a bucket of the same name")
    void uploadIdIsNotACapability() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");
        Map<String, Object> part = partBody('a', 10);
        putPart("media", uploadId, 1, part);

        UUID intruder = UUID.randomUUID();
        createBucket(intruder, "media", false);

        mockMvc.perform(put(partPath("media", uploadId, 2))
                        .header("Authorization", bearer(intruder))
                        .contentType("application/json")
                        .content(json(partBody('b', 10))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BUCKETS + "/media/multipart-uploads/" + uploadId + "/parts")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        complete("media", uploadId, intruder, completion(1, etagOf(part))).andExpect(status().isNotFound());
        mockMvc.perform(delete(BUCKETS + "/media/multipart-uploads/" + uploadId)
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());

        assertThat(sessionRows(uploadId)).isOne();
        assertThat(stagedChunkRows(uploadId)).isOne();
        complete("media", uploadId, tenantId, completion(1, etagOf(part))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("completing into a versioned bucket adds a version, as a single PUT would")
    void completionVersionsLikeAPut() throws Exception {
        createBucket(tenantId, "archive", true);
        mockMvc.perform(put(BUCKETS + "/archive/objects/report.pdf")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of(
                                "sizeBytes", 4,
                                "contentHash", Sha256.composite(List.of(digest('f'))),
                                "chunks", List.of(Map.of("index", 0, "digest", digest('f'), "sizeBytes", 4,
                                        "nodeId", "blob-node-0"))))))
                .andExpect(status().isCreated());

        UUID uploadId = initiate("archive", "report.pdf");
        Map<String, Object> part = partBody('a', 10);
        putPart("archive", uploadId, 1, part);
        complete("archive", uploadId, tenantId, completion(1, etagOf(part))).andExpect(status().isCreated());

        mockMvc.perform(get(BUCKETS + "/archive/object-versions/report.pdf")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(BUCKETS + "/archive/objects/report.pdf")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(jsonPath("$.sizeBytes").value(10));
    }

    @Test
    @DisplayName("rejects parts that do not describe their content, or fall outside the part range")
    void validatesParts() throws Exception {
        createBucket(tenantId, "media", false);
        UUID uploadId = initiate("media", "clip.mp4");

        Map<String, Object> wrongHash = new HashMap<>(partBody('a', 10));
        wrongHash.put("contentHash", digest('e'));
        Map<String, Object> wrongSize = new HashMap<>(partBody('a', 10));
        wrongSize.put("sizeBytes", 11);
        Map<String, Object> empty = Map.of("sizeBytes", 1, "contentHash", digest('a'), "chunks", List.of());

        for (Map<String, Object> body : List.of(wrongHash, wrongSize, empty)) {
            mockMvc.perform(put(partPath("media", uploadId, 1))
                            .header("Authorization", bearer(tenantId))
                            .contentType("application/json")
                            .content(json(body)))
                    .andExpect(status().isBadRequest());
        }
        for (int partNumber : new int[]{0, 10_001}) {
            mockMvc.perform(put(partPath("media", uploadId, partNumber))
                            .header("Authorization", bearer(tenantId))
                            .contentType("application/json")
                            .content(json(partBody('a', 10))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(stagedChunkRows(uploadId)).isZero();

        complete("media", uploadId, tenantId, Map.of("parts", List.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown upload or bucket is not found")
    void unknownUploadOrBucket() throws Exception {
        createBucket(tenantId, "media", false);

        mockMvc.perform(put(partPath("media", UUID.randomUUID(), 1))
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(partBody('a', 10))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BUCKETS + "/absent/multipart-uploads")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("key", "clip.mp4"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("requires a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(BUCKETS + "/media/multipart-uploads")
                        .contentType("application/json")
                        .content(json(Map.of("key", "clip.mp4"))))
                .andExpect(status().isUnauthorized());
    }
}
