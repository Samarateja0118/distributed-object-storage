package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectTransferApiIT extends AbstractUploadOrchestratorIT {

    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_DIGEST = Sha256.hexOfContent(CONTENT);

    @Test
    @DisplayName("stores the bytes under their digest, then registers the metadata pointing at it")
    void uploadStoresBytesThenRegistersMetadata() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CONTENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentHash").value(CONTENT_DIGEST))
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
                .containsEntry("contentHash", CONTENT_DIGEST)
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
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + CONTENT_DIGEST + "\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(CONTENT);
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

    @Test
    @DisplayName("rejects an object larger than the configured limit")
    void rejectsObjectOverTheSizeLimit() throws Exception {
        byte[] tooLarge = new byte[(int) (1024 * 1024) + 1];

        mockMvc.perform(put("/api/v1/buckets/photos/objects/big.bin")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(tooLarge))
                .andExpect(status().isPayloadTooLarge());

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }
}
