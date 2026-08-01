package com.dfs.integration;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cross-service contracts, exercised against real services rather than
 * stand-ins. Each test here asserts something no single-service suite can:
 * every one of them spans at least two processes.
 */
class ObjectLifecycleE2EIT extends AbstractClusterIT {

    @Test
    void storesAndServesAnObjectThroughEveryService() throws Exception {
        Tenant tenant = newTenant();
        byte[] content = "the quick brown fox".repeat(100).getBytes(StandardCharsets.UTF_8);

        assertThat(createBucket(tenant, "photos").statusCode()).isEqualTo(201);

        HttpResponse<String> uploaded = upload(tenant, "photos", "fox.txt", content);
        assertThat(uploaded.statusCode()).isEqualTo(201);

        // The object's name is the composite of its chunk digests, not the hash of
        // its bytes (ADR 9). This content fits in one chunk, so the composite is
        // over a single digest — and is still emphatically not that digest.
        String objectDigest = body(uploaded).get("contentHash").asText();
        String chunkDigest = Sha256.hexOfContent(content);
        assertThat(objectDigest).isEqualTo(Sha256.composite(List.of(chunkDigest)));
        assertThat(objectDigest).isNotEqualTo(chunkDigest);

        // The chunk digest is what the blob node filed the bytes under, and that
        // is the only thing tying the two halves together.
        assertThat(blobFilesFor(chunkDigest)).isEqualTo(1);
        assertThat(blobFilesFor(objectDigest)).isZero();

        HttpResponse<byte[]> downloaded = download(tenant, "photos", "fox.txt");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);
    }

    /**
     * An object that spans chunks, across every real service. The single-service
     * suites stub one side or the other; only here do the orchestrator's chunk
     * boundaries, the metadata service's stored list, and the blob node's actual
     * files all have to agree.
     */
    @Test
    void storesAndReassemblesAMultiChunkObject() throws Exception {
        Tenant tenant = newTenant();
        // Larger than the orchestrator's configured chunk size, so it cannot land
        // in a single chunk however the bytes arrive.
        byte[] content = new byte[9 * 1024 * 1024 + 511];
        new Random(1234).nextBytes(content);

        assertThat(createBucket(tenant, "large").statusCode()).isEqualTo(201);

        HttpResponse<String> uploaded = upload(tenant, "large", "video.bin", content);
        assertThat(uploaded.statusCode()).isEqualTo(201);
        assertThat(body(uploaded).get("sizeBytes").asLong()).isEqualTo(content.length);

        HttpResponse<byte[]> downloaded = download(tenant, "large", "video.bin");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        // Byte-for-byte: a wrong chunk order or a dropped boundary would still
        // produce the right length.
        assertThat(downloaded.body()).isEqualTo(content);
    }

    /**
     * The tenancy boundary, end to end. Both tenants use the same bucket name and
     * the same key; only the token differs. If the orchestrator forwarded anything
     * other than the caller's own token, or the metadata service trusted anything
     * but the token's claim, this passes when it should not.
     */
    @Test
    void doesNotServeOneTenantTheOthersObject() throws Exception {
        Tenant owner = newTenant();
        Tenant stranger = newTenant();
        byte[] owned = "private".getBytes(StandardCharsets.UTF_8);
        byte[] strangers = "also private".getBytes(StandardCharsets.UTF_8);

        assertThat(createBucket(owner, "shared-name").statusCode()).isEqualTo(201);
        assertThat(createBucket(stranger, "shared-name").statusCode()).isEqualTo(201);

        assertThat(upload(owner, "shared-name", "secret.txt", owned).statusCode()).isEqualTo(201);

        HttpResponse<byte[]> theft = download(stranger, "shared-name", "secret.txt");
        assertThat(theft.statusCode()).isEqualTo(404);

        // The 404 has to mean "not yours", not "no such bucket". The stranger can
        // write the identical bucket and key and read back their own bytes, while
        // the owner still reads theirs — one namespace per tenant, not one shared.
        assertThat(upload(stranger, "shared-name", "secret.txt", strangers).statusCode()).isEqualTo(201);
        assertThat(download(stranger, "shared-name", "secret.txt").body()).isEqualTo(strangers);
        assertThat(download(owner, "shared-name", "secret.txt").body()).isEqualTo(owned);
    }

    /**
     * ADR 4: deleting an object removes the pointer, never the bytes. The bytes
     * may still be referenced by another tenant's object, so only GC may reclaim
     * them — and this is the only test in the repository that can observe both
     * sides of that at once.
     */
    @Test
    void deletingAnObjectLeavesItsBytesOnDisk() throws Exception {
        Tenant tenant = newTenant();
        byte[] content = "delete me".repeat(10).getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        createBucket(tenant, "trash");
        assertThat(upload(tenant, "trash", "doomed.txt", content).statusCode()).isEqualTo(201);
        assertThat(blobFilesFor(digest)).isEqualTo(1);

        assertThat(deleteObject(tenant, "trash", "doomed.txt").statusCode()).isEqualTo(204);

        assertThat(download(tenant, "trash", "doomed.txt").statusCode()).isEqualTo(404);
        assertThat(blobFilesFor(digest)).isEqualTo(1);
    }

    /**
     * ADR 3: content addressing means identical bytes are one file, even across
     * tenants that cannot see each other. Two isolated tenants, one blob.
     */
    @Test
    void storesIdenticalContentFromTwoTenantsOnce() throws Exception {
        Tenant first = newTenant();
        Tenant second = newTenant();
        byte[] content = "identical bytes from unrelated tenants".getBytes(StandardCharsets.UTF_8);

        createBucket(first, "one");
        createBucket(second, "two");

        String firstDigest = body(upload(first, "one", "a.txt", content)).get("contentHash").asText();
        String secondDigest = body(upload(second, "two", "b.txt", content)).get("contentHash").asText();

        // Identical content gets one object name, because the composite is a
        // function of the chunk digests and those are a function of the bytes.
        assertThat(firstDigest).isEqualTo(secondDigest);
        // The file on disk is named by the chunk's digest, not the object's.
        assertThat(blobFilesFor(Sha256.hexOfContent(content))).isEqualTo(1);

        // Deduplicated, but still independently readable by each owner.
        assertThat(download(first, "one", "a.txt").body()).isEqualTo(content);
        assertThat(download(second, "two", "b.txt").body()).isEqualTo(content);
    }

    @Test
    void refusesUnauthenticatedRequests() throws Exception {
        HttpResponse<String> anonymous = send(HttpRequest.newBuilder(
                        objectUri(DfsCluster.orchestratorUrl(), "photos", "fox.txt"))
                .GET());
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    /**
     * The blob node authenticates too. A caller who somehow learned a digest still
     * cannot bypass the metadata service to fetch the bytes behind it.
     */
    @Test
    void refusesUnauthenticatedBlobReads() throws Exception {
        Tenant tenant = newTenant();
        byte[] content = "guessable".getBytes(StandardCharsets.UTF_8);
        createBucket(tenant, "direct");
        upload(tenant, "direct", "c.txt", content);

        HttpResponse<String> direct = send(HttpRequest.newBuilder(
                        URI.create(DfsCluster.blobUrl() + "/internal/v1/blobs/" + Sha256.hexOfContent(content)))
                .GET());
        assertThat(direct.statusCode()).isEqualTo(401);
    }
}
