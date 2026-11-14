package com.dfs.integration;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multipart upload across every real service (ADR 12): the session in the
 * metadata service, each part's bytes through the orchestrator onto the blob
 * node, and the completed object read back through the orchestrator.
 */
class MultipartUploadE2EIT extends AbstractClusterIT {

    /** The metadata service's default minimum part size. */
    private static final int MIN_PART = 5 * 1024 * 1024;

    private static String initiate(Tenant tenant, String bucket, String key) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(DfsCluster.metadataUrl() + "/api/v1/buckets/" + bucket + "/multipart-uploads"))
                .header("Authorization", "Bearer " + tenant.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                        Map.of("key", key, "contentType", "application/octet-stream")))));
        assertThat(response.statusCode()).isEqualTo(201);
        return body(response).get("uploadId").asText();
    }

    private static HttpResponse<String> uploadPart(
            Tenant tenant, String bucket, String uploadId, int partNumber, byte[] content) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(DfsCluster.orchestratorUrl() + "/api/v1/buckets/" + bucket
                        + "/multipart-uploads/" + uploadId + "/parts/" + partNumber))
                .header("Authorization", "Bearer " + tenant.token())
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)));
    }

    private static HttpResponse<String> complete(
            Tenant tenant, String bucket, String uploadId, List<Map<String, Object>> parts) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(DfsCluster.metadataUrl() + "/api/v1/buckets/" + bucket
                        + "/multipart-uploads/" + uploadId + "/complete"))
                .header("Authorization", "Bearer " + tenant.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("parts", parts)))));
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    @Test
    void completesAnObjectFromPartsSentThroughTheOrchestrator() throws Exception {
        Tenant tenant = newTenant();
        assertThat(createBucket(tenant, "uploads").statusCode()).isEqualTo(201);
        String uploadId = initiate(tenant, "uploads", "archive.bin");

        byte[] first = randomBytes(MIN_PART + 3, 21);
        byte[] second = randomBytes(1_000, 22);
        HttpResponse<String> firstPart = uploadPart(tenant, "uploads", uploadId, 1, first);
        HttpResponse<String> secondPart = uploadPart(tenant, "uploads", uploadId, 2, second);
        assertThat(firstPart.statusCode()).isEqualTo(200);
        assertThat(secondPart.statusCode()).isEqualTo(200);

        // Each part fits in one chunk at the default chunk size, so each part's
        // etag is the composite over that one chunk's digest.
        String firstChunk = Sha256.hexOfContent(first);
        String secondChunk = Sha256.hexOfContent(second);
        assertThat(body(firstPart).get("etag").asText()).isEqualTo(Sha256.composite(List.of(firstChunk)));

        HttpResponse<String> completed = complete(tenant, "uploads", uploadId, List.of(
                Map.of("partNumber", 1, "etag", body(firstPart).get("etag").asText()),
                Map.of("partNumber", 2, "etag", body(secondPart).get("etag").asText())));
        assertThat(completed.statusCode()).isEqualTo(201);
        // Named by every chunk of every part in order, as a single upload is (ADR 9).
        assertThat(body(completed).get("contentHash").asText())
                .isEqualTo(Sha256.composite(List.of(firstChunk, secondChunk)));
        assertThat(body(completed).get("sizeBytes").asLong()).isEqualTo(first.length + second.length);

        byte[] expected = new byte[first.length + second.length];
        System.arraycopy(first, 0, expected, 0, first.length);
        System.arraycopy(second, 0, expected, first.length, second.length);
        HttpResponse<byte[]> downloaded = download(tenant, "uploads", "archive.bin");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(expected);

        assertThat(blobFilesFor(firstChunk)).isEqualTo(1);
        assertThat(blobFilesFor(secondChunk)).isEqualTo(1);
    }

    /**
     * Aborting ends the session and nothing else: the part's bytes stay on disk,
     * unreferenced, for GC — the same state a deleted object leaves (ADR 4).
     */
    @Test
    void abortingAnUploadLeavesItsBytesForGc() throws Exception {
        Tenant tenant = newTenant();
        createBucket(tenant, "aborted");
        String uploadId = initiate(tenant, "aborted", "never.bin");
        byte[] part = randomBytes(2_000, 31);
        assertThat(uploadPart(tenant, "aborted", uploadId, 1, part).statusCode()).isEqualTo(200);

        HttpResponse<String> aborted = send(HttpRequest.newBuilder(URI.create(DfsCluster.metadataUrl()
                        + "/api/v1/buckets/aborted/multipart-uploads/" + uploadId))
                .header("Authorization", "Bearer " + tenant.token())
                .DELETE());
        assertThat(aborted.statusCode()).isEqualTo(204);

        assertThat(uploadPart(tenant, "aborted", uploadId, 2, part).statusCode()).isEqualTo(404);
        assertThat(download(tenant, "aborted", "never.bin").statusCode()).isEqualTo(404);
        assertThat(blobFilesFor(Sha256.hexOfContent(part))).isEqualTo(1);
    }

    /**
     * The tenancy boundary for sessions, end to end. The orchestrator forwards
     * the caller's own token, so a stranger holding the owner's upload id — with
     * a bucket of the same name — is refused by the metadata service, and the
     * owner's upload completes untouched.
     */
    @Test
    void anUploadIdDoesNotLetAnotherTenantAddParts() throws Exception {
        Tenant owner = newTenant();
        Tenant stranger = newTenant();
        createBucket(owner, "same-name");
        createBucket(stranger, "same-name");
        String uploadId = initiate(owner, "same-name", "owned.bin");

        byte[] intruding = randomBytes(500, 41);
        assertThat(uploadPart(stranger, "same-name", uploadId, 1, intruding).statusCode()).isEqualTo(404);

        byte[] owned = randomBytes(700, 42);
        HttpResponse<String> part = uploadPart(owner, "same-name", uploadId, 1, owned);
        assertThat(part.statusCode()).isEqualTo(200);
        assertThat(complete(owner, "same-name", uploadId,
                List.of(Map.of("partNumber", 1, "etag", body(part).get("etag").asText()))).statusCode())
                .isEqualTo(201);
        assertThat(download(owner, "same-name", "owned.bin").body()).isEqualTo(owned);
    }
}
