package com.dfs.upload;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens when one of the two writes fails.
 *
 * <p>There is no transaction across the blob node and the metadata service, so
 * these tests pin down which inconsistency the orchestrator is willing to leave
 * behind — see {@link com.dfs.upload.application.service.ObjectTransferService#upload}.
 */
class ObjectTransferFailureIT extends AbstractUploadOrchestratorIT {

    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("a failed blob write leaves no metadata behind, so no object points at missing bytes")
    void blobFailureDoesNotRegisterMetadata() throws Exception {
        DOWNSTREAM.failBlobWith(500);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isBadGateway());

        // The invariant: metadata is never written for content that was not stored.
        assertThat(DOWNSTREAM.metadataRequests()).isEmpty();
    }

    @Test
    @DisplayName("a failed metadata write leaves the stored bytes unreferenced rather than half-registered")
    void metadataFailureLeavesOnlyAnOrphanedBlob() throws Exception {
        DOWNSTREAM.failMetadataWith(500);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isBadGateway());

        // The bytes were written and are now unreferenced. That is the accepted
        // outcome: nothing can observe them, and GC reclaims them in Phase 3.
        assertThat(DOWNSTREAM.hasBlob(Sha256.hexOfContent(CONTENT))).isTrue();

        // With the metadata service healthy again, nothing is readable at that
        // key — proving no half-written record was left behind. A client sees a
        // failed upload, not a broken object.
        DOWNSTREAM.failMetadataWith(0);
        mockMvc.perform(get("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("retrying a failed upload reuses the same digest instead of orphaning more bytes")
    void retryAfterMetadataFailureReusesTheSameBlob() throws Exception {
        DOWNSTREAM.failMetadataWith(500);
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isBadGateway());

        DOWNSTREAM.failMetadataWith(0);
        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isCreated());

        assertThat(DOWNSTREAM.blobRequests())
                .hasSize(2)
                .allSatisfy(request -> assertThat(request.path())
                        .isEqualTo("/internal/v1/blobs/" + Sha256.hexOfContent(CONTENT)));
    }

    @Test
    @DisplayName("a missing object is a 404, not a gateway error")
    void missingObjectIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/buckets/photos/objects/absent.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Object not found"));
    }

    @Test
    @DisplayName("metadata referring to a blob the node does not have reports as not found")
    void danglingMetadataReportsNotFound() throws Exception {
        DOWNSTREAM.registerDanglingObject("photos", "ghost.txt", Sha256.hexOfContent(CONTENT));

        mockMvc.perform(get("/api/v1/buckets/photos/objects/ghost.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a downstream rejection of the caller's token surfaces as 401, not as a gateway error")
    void downstreamAuthFailureSurfacesAsUnauthorized() throws Exception {
        DOWNSTREAM.failBlobWith(401);

        mockMvc.perform(put("/api/v1/buckets/photos/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("uploading to a bucket that does not exist is the caller's error")
    void unknownBucketIsNotFound() throws Exception {
        DOWNSTREAM.failMetadataWith(404);

        mockMvc.perform(put("/api/v1/buckets/absent/objects/fox.txt")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .content(CONTENT))
                .andExpect(status().isNotFound());
    }
}
