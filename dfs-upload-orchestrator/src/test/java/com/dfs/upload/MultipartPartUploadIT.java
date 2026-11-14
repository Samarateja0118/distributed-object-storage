package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.domain.placement.RendezvousPlacement;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one multipart call that carries bytes (ADR 12): a part is chunked and
 * placed like any upload, then registered with its session in the metadata
 * service. Opening, completing and aborting sessions are the metadata
 * service's and are tested there.
 */
class MultipartPartUploadIT extends AbstractUploadOrchestratorIT {

    private static final int CHUNK = 64 * 1024;

    @Autowired
    private RendezvousPlacement placement;

    private final UUID uploadId = UUID.randomUUID();

    private String partPath(int partNumber) {
        return "/api/v1/buckets/media/multipart-uploads/" + uploadId + "/parts/" + partNumber;
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    @Test
    @DisplayName("a part is chunked and placed, then registered with its own chunk list and etag")
    @SuppressWarnings("unchecked")
    void storesAndRegistersAPart() throws Exception {
        byte[] content = randomBytes(CHUNK * 2 + 500, 3);

        String etag = mockMvc.perform(put(partPath(2))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(2))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        var puts = DOWNSTREAM.blobCalls().stream().filter(call -> call.operation().equals("PutChunk")).toList();
        assertThat(puts).hasSize(3);
        assertThat(puts.getLast().content()).hasSize(500);
        // Each chunk on its own first choice, as for a single upload (ADR 11).
        assertThat(puts).allSatisfy(call ->
                assertThat(call.nodeId()).isEqualTo(placement.rank(call.digest()).getFirst()));

        // The part's etag is the composite digest of its own chunks (ADR 9).
        String expectedEtag = Sha256.composite(puts.stream().map(DownstreamStub.BlobCall::digest).toList());
        assertThat(etag).isEqualTo("\"" + expectedEtag + "\"");

        Map<String, Object> registered = DOWNSTREAM.registeredPart("media", uploadId, 2);
        assertThat(registered).isNotNull();
        assertThat(registered).containsEntry("contentHash", expectedEtag);
        assertThat(((Number) registered.get("sizeBytes")).longValue()).isEqualTo(content.length);
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) registered.get("chunks");
        assertThat(chunks).hasSize(3);
        for (int i = 0; i < chunks.size(); i++) {
            // Indexed from zero within the part: every part is chunked on its own.
            assertThat(chunks.get(i)).containsEntry("index", i);
            assertThat(chunks.get(i)).containsEntry("digest", puts.get(i).digest());
            assertThat(chunks.get(i)).containsEntry("nodeId", puts.get(i).nodeId());
        }

        // The caller's own token went to both halves (ADR 7).
        String callerToken = DOWNSTREAM.metadataRequests().getFirst().authorization();
        assertThat(callerToken).startsWith("Bearer ");
        assertThat(puts).allSatisfy(call -> assertThat(call.authorization()).isEqualTo(callerToken));
    }

    @Test
    @DisplayName("each part is chunked independently, so identical parts store identical chunks")
    void partsAreChunkedIndependently() throws Exception {
        byte[] content = randomBytes(CHUNK + 10, 9);

        for (int partNumber : new int[]{1, 2}) {
            mockMvc.perform(put(partPath(partNumber))
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .content(content))
                    .andExpect(status().isOk());
        }

        assertThat(DOWNSTREAM.registeredPart("media", uploadId, 1).get("contentHash"))
                .isEqualTo(DOWNSTREAM.registeredPart("media", uploadId, 2).get("contentHash"));
    }

    @Test
    @DisplayName("blob-first: a failed chunk write registers nothing with the session")
    void blobFailureRegistersNothing() throws Exception {
        DOWNSTREAM.failBlobWith(Status.INTERNAL);

        mockMvc.perform(put(partPath(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(randomBytes(100, 1)))
                .andExpect(status().isBadGateway());

        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("an upload the metadata service does not have open is a 404, with the bytes left unreferenced")
    void unknownUploadIsNotFound() throws Exception {
        byte[] content = randomBytes(100, 2);
        DOWNSTREAM.failMetadataWith(404);

        mockMvc.perform(put(partPath(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(content))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Upload not found"));

        // Blob-first: the chunk was stored before the session was asked, and is
        // now unreferenced bytes for GC (ADR 6).
        assertThat(DOWNSTREAM.hasBlob(Sha256.hexOfContent(content))).isTrue();
    }

    @Test
    @DisplayName("a part the metadata service refuses as invalid is the caller's 400, not a gateway error")
    void refusedPartIsABadRequest() throws Exception {
        DOWNSTREAM.failMetadataWith(400);

        mockMvc.perform(put(partPath(10_001))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(randomBytes(100, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid part"))
                // The metadata service's own reason, passed through.
                .andExpect(jsonPath("$.detail").value("forced metadata failure"));
    }

    @Test
    @DisplayName("an empty part is refused without asking the metadata service")
    void emptyPartIsRefused() throws Exception {
        mockMvc.perform(put(partPath(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid part"));

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("each part is held to the per-request size ceiling, registering nothing over it")
    void partOverTheRequestLimitIsRefused() throws Exception {
        mockMvc.perform(put(partPath(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(new byte[1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge());

        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("a part needs a token, and nothing downstream is contacted without one")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(put(partPath(1)).content(randomBytes(10, 5)))
                .andExpect(status().isUnauthorized());

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("a malformed upload id is refused before any bytes are stored")
    void malformedUploadIdIsRefused() throws Exception {
        mockMvc.perform(put("/api/v1/buckets/media/multipart-uploads/not-a-uuid/parts/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(randomBytes(10, 6)))
                .andExpect(status().isBadRequest());

        assertThat(DOWNSTREAM.blobCalls()).isEmpty();
    }
}
