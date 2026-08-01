package com.dfs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Drives the cluster the way a client would: over HTTP, with a token the auth
 * service actually issued.
 */
abstract class AbstractClusterIT {

    protected static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void startCluster() {
        DfsCluster.ensureRunning();
    }

    /**
     * A tenant with a token obtained by registering and then logging in, rather
     * than one signed locally. Signing a token in the test would prove only that
     * the validators agree with the test; issuing one proves they agree with the
     * auth service.
     */
    protected record Tenant(String slug, String token) {
    }

    protected static Tenant newTenant() throws Exception {
        String slug = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase(Locale.ROOT);
        String email = slug + "@example.test";
        String password = "correct-horse-battery-staple";

        HttpResponse<String> registered = send(HttpRequest.newBuilder(URI.create(DfsCluster.authUrl() + "/api/v1/tenants"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(java.util.Map.of(
                        "name", slug,
                        "slug", slug,
                        "ownerEmail", email,
                        "ownerPassword", password)))));
        if (registered.statusCode() != 201) {
            throw new IllegalStateException("Tenant registration failed: " + registered.statusCode() + " " + registered.body());
        }

        HttpResponse<String> loggedIn = send(HttpRequest.newBuilder(URI.create(DfsCluster.authUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(java.util.Map.of(
                        "email", email,
                        "password", password)))));
        if (loggedIn.statusCode() != 200) {
            throw new IllegalStateException("Login failed: " + loggedIn.statusCode() + " " + loggedIn.body());
        }

        return new Tenant(slug, JSON.readTree(loggedIn.body()).get("accessToken").asText());
    }

    protected static HttpResponse<String> createBucket(Tenant tenant, String bucket) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(DfsCluster.metadataUrl() + "/api/v1/buckets/" + bucket))
                .header("Authorization", "Bearer " + tenant.token())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"versioningEnabled\":false}")));
    }

    protected static HttpResponse<String> upload(Tenant tenant, String bucket, String key, byte[] content)
            throws Exception {
        return send(HttpRequest.newBuilder(objectUri(DfsCluster.orchestratorUrl(), bucket, key))
                .header("Authorization", "Bearer " + tenant.token())
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)));
    }

    protected static HttpResponse<byte[]> download(Tenant tenant, String bucket, String key) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(objectUri(DfsCluster.orchestratorUrl(), bucket, key))
                .header("Authorization", "Bearer " + tenant.token())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Deletes through the metadata service, which is the only place an object can be removed. */
    protected static HttpResponse<String> deleteObject(Tenant tenant, String bucket, String key) throws Exception {
        return send(HttpRequest.newBuilder(objectUri(DfsCluster.metadataUrl(), bucket, key))
                .header("Authorization", "Bearer " + tenant.token())
                .DELETE());
    }

    protected static JsonNode body(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body());
    }

    /**
     * How many files on the blob node's disk are named by this digest. Blobs are
     * stored at {@code <root>/<shard>/<shard>/<digest>}, so the count answers
     * whether identical content was stored once or twice.
     */
    protected static long blobFilesFor(String digest) throws IOException {
        try (Stream<Path> paths = Files.walk(DfsCluster.blobRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(digest))
                    .count();
        }
    }

    protected static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected static URI objectUri(String baseUrl, String bucket, String key) {
        return URI.create(baseUrl + "/api/v1/buckets/" + bucket + "/objects/"
                + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
