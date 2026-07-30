package com.dfs.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the blob node and the metadata service over real HTTP.
 *
 * <p>A real socket rather than a mocked client on purpose: what these tests are
 * checking is the wire contract — the digest in the URL, the forwarded
 * {@code Authorization} header, the JSON field names. A mock would be
 * configured with this service's own assumptions and would agree with them
 * whether or not they were right.
 *
 * <p>Both dependencies are served by one server, distinguished by path, and
 * each keeps enough state to behave like the real thing so a round trip can be
 * tested end to end. {@link #failBlobWith} and {@link #failMetadataWith} force
 * the failure cases.
 */
final class DownstreamStub {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;

    private final List<RecordedRequest> blobRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<RecordedRequest> metadataRequests = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> objects = new ConcurrentHashMap<>();

    private volatile int blobFailureStatus;
    private volatile int metadataFailureStatus;

    DownstreamStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the downstream stub", e);
        }
        server.createContext("/internal/v1/blobs", this::handleBlob);
        server.createContext("/api/v1/buckets", this::handleMetadata);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
        blobRequests.clear();
        metadataRequests.clear();
        blobs.clear();
        objects.clear();
        blobFailureStatus = 0;
        metadataFailureStatus = 0;
    }

    void failBlobWith(int status) {
        blobFailureStatus = status;
    }

    void failMetadataWith(int status) {
        metadataFailureStatus = status;
    }

    /** Pre-loads metadata pointing at a digest the blob node does not have. */
    void registerDanglingObject(String bucket, String key, String digest) {
        objects.put(bucket + "/" + key, objectRecord(key, digest, 12, "text/plain", Map.of()));
    }

    /**
     * Pre-loads a complete object whose content type is absent, which the
     * metadata API permits for records not written through the orchestrator.
     */
    void registerObjectWithoutContentType(String bucket, String key, String digest, byte[] content) {
        blobs.put(digest, content);
        objects.put(bucket + "/" + key, objectRecord(key, digest, content.length, null, Map.of()));
    }

    List<RecordedRequest> blobRequests() {
        return List.copyOf(blobRequests);
    }

    List<RecordedRequest> metadataRequests() {
        return List.copyOf(metadataRequests);
    }

    boolean hasBlob(String digest) {
        return blobs.containsKey(digest);
    }

    private void handleBlob(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String digest = exchange.getRequestURI().getPath().substring("/internal/v1/blobs/".length());
        blobRequests.add(RecordedRequest.from(exchange, body));

        if (blobFailureStatus != 0) {
            respond(exchange, blobFailureStatus, "{\"detail\":\"forced blob failure\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }

        switch (exchange.getRequestMethod()) {
            case "PUT" -> {
                blobs.put(digest, body);
                respondJson(exchange, 201, Map.of("digest", digest, "sizeBytes", body.length));
            }
            case "GET" -> {
                byte[] content = blobs.get(digest);
                if (content == null) {
                    respond(exchange, 404, "{\"detail\":\"no such blob\"}".getBytes(StandardCharsets.UTF_8));
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                    respond(exchange, 200, content);
                }
            }
            default -> respond(exchange, 405, new byte[0]);
        }
    }

    private void handleMetadata(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        metadataRequests.add(RecordedRequest.from(exchange, body));

        if (metadataFailureStatus != 0) {
            respond(exchange, metadataFailureStatus,
                    "{\"detail\":\"forced metadata failure\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }

        // /api/v1/buckets/{bucket}/objects/{key...}
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        String remainder = path.substring("/api/v1/buckets/".length());
        int objectsAt = remainder.indexOf("/objects/");
        if (objectsAt < 0) {
            respond(exchange, 404, new byte[0]);
            return;
        }
        String bucket = remainder.substring(0, objectsAt);
        String key = remainder.substring(objectsAt + "/objects/".length());

        switch (exchange.getRequestMethod()) {
            case "PUT" -> {
                Map<String, Object> request = JSON.readValue(body, Map.class);
                Map<String, Object> stored = objectRecord(
                        key,
                        (String) request.get("contentHash"),
                        ((Number) request.get("sizeBytes")).longValue(),
                        (String) request.get("contentType"),
                        (Map<String, String>) request.getOrDefault("userMetadata", Map.of()));
                objects.put(bucket + "/" + key, stored);
                respondJson(exchange, 201, stored);
            }
            case "GET" -> {
                Map<String, Object> stored = objects.get(bucket + "/" + key);
                if (stored == null) {
                    respond(exchange, 404, "{\"detail\":\"no such object\"}".getBytes(StandardCharsets.UTF_8));
                } else {
                    respondJson(exchange, 200, stored);
                }
            }
            default -> respond(exchange, 405, new byte[0]);
        }
    }

    private static Map<String, Object> objectRecord(
            String key, String digest, long sizeBytes, String contentType, Map<String, String> userMetadata) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("key", key);
        record.put("versionId", UUID.randomUUID().toString());
        record.put("sizeBytes", sizeBytes);
        // Stored as given, null included: the real API allows an object with no
        // content type, and the orchestrator has to cope with reading one back.
        record.put("contentType", contentType);
        record.put("contentHash", digest);
        record.put("etag", digest);
        record.put("userMetadata", userMetadata);
        // Fields the real service returns that the orchestrator does not read;
        // present so the tests would catch it starting to depend on them.
        record.put("id", UUID.randomUUID().toString());
        record.put("latest", true);
        return record;
    }

    private static void respondJson(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, status, JSON.writeValueAsBytes(body));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    record RecordedRequest(String method, String path, Map<String, String> headers, byte[] body) {

        static RecordedRequest from(HttpExchange exchange, byte[] body) {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(), String.join(",", values)));
            return new RecordedRequest(
                    exchange.getRequestMethod(),
                    URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8),
                    headers,
                    body);
        }

        String authorization() {
            return headers.get("authorization");
        }

        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
