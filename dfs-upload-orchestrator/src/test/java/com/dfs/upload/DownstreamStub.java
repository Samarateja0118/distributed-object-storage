package com.dfs.upload;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.proto.blob.v1.ChunkExistsRequest;
import com.dfs.proto.blob.v1.ChunkExistsResponse;
import com.dfs.proto.blob.v1.GetChunkRequest;
import com.dfs.proto.blob.v1.GetChunkResponse;
import com.dfs.proto.blob.v1.PutChunkRequest;
import com.dfs.proto.blob.v1.PutChunkResponse;
import com.dfs.proto.common.v1.ChunkId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the blob node and the metadata service, each on the transport it
 * really uses: gRPC for the byte path, HTTP for metadata.
 *
 * <p>Real servers rather than mocked clients on purpose: what these tests check
 * is the wire contract — the digest and frames the node receives, the forwarded
 * {@code Authorization}, the JSON field names. A mock would be configured with
 * this service's own assumptions and would agree with them whether or not they
 * were right.
 *
 * <p>Each half keeps enough state to behave like the real thing, so a round trip
 * can be tested end to end. {@link #failBlobWith} and {@link #failMetadataWith}
 * force the failure cases.
 */
final class DownstreamStub {

    /**
     * Two nodes, so placement has something to choose between and a write has
     * somewhere to fail over to. In-process: no ports, nothing to clean up.
     */
    static final List<String> NODE_IDS = List.of("blob-node-0", "blob-node-1");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> CALLER_AUTHORIZATION = Context.key("stub-authorization");

    private final HttpServer metadataServer;
    private final Map<String, BlobNode> nodes = new LinkedHashMap<>();

    private final List<BlobCall> blobCalls = Collections.synchronizedList(new ArrayList<>());
    private final List<RecordedRequest> metadataRequests = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Map<String, Object>> objects = new ConcurrentHashMap<>();

    /** Chunk lists, keyed like {@link #objects} and served from their own path. */
    private final Map<String, List<Map<String, Object>>> objectChunks = new ConcurrentHashMap<>();

    private volatile int metadataFailureStatus;

    DownstreamStub() {
        try {
            metadataServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            metadataServer.createContext("/api/v1/buckets", this::handleMetadata);
            metadataServer.start();

            for (String nodeId : NODE_IDS) {
                nodes.put(nodeId, new BlobNode(nodeId));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the downstream stub", e);
        }
    }

    String metadataBaseUrl() {
        return "http://127.0.0.1:" + metadataServer.getAddress().getPort();
    }

    static String blobGrpcTarget(String nodeId) {
        return "in-process:upload-blob-node-stub-" + nodeId;
    }

    void reset() {
        blobCalls.clear();
        metadataRequests.clear();
        objects.clear();
        objectChunks.clear();
        nodes.values().forEach(BlobNode::reset);
        metadataFailureStatus = 0;
    }

    /** Fails every blob call on every node; {@code null} restores success. */
    void failBlobWith(Status status) {
        nodes.values().forEach(node -> node.failure = status);
    }

    /** Takes one node down, leaving the rest of the cluster answering. */
    void failNode(String nodeId, Status status) {
        nodes.get(nodeId).failure = status;
    }

    void failMetadataWith(int status) {
        metadataFailureStatus = status;
    }

    /** Pre-loads metadata pointing at a chunk the blob node does not have. */
    void registerDanglingObject(String bucket, String key, String digest) {
        objects.put(bucket + "/" + key, objectRecord(key, digest, 12, "text/plain", Map.of()));
        objectChunks.put(bucket + "/" + key, List.of(chunkRecord(0, digest, 12)));
    }

    /**
     * Pre-loads a complete object whose content type is absent, which the
     * metadata API permits for records not written through the orchestrator.
     */
    void registerObjectWithoutContentType(String bucket, String key, String digest, byte[] content) {
        nodes.get("blob-node-0").blobs.put(digest, content);
        objects.put(bucket + "/" + key, objectRecord(key, digest, content.length, null, Map.of()));
        objectChunks.put(bucket + "/" + key, List.of(chunkRecord(0, digest, content.length)));
    }

    List<BlobCall> blobCalls() {
        return List.copyOf(blobCalls);
    }

    List<RecordedRequest> metadataRequests() {
        return List.copyOf(metadataRequests);
    }

    boolean hasBlob(String digest) {
        return nodes.values().stream().anyMatch(node -> node.blobs.containsKey(digest));
    }

    boolean hasBlob(String nodeId, String digest) {
        return nodes.get(nodeId).blobs.containsKey(digest);
    }

    /** Which node, if any, took this chunk. */
    Optional<String> nodeHolding(String digest) {
        return nodes.values().stream()
                .filter(node -> node.blobs.containsKey(digest))
                .map(node -> node.id)
                .findFirst();
    }

    /**
     * The blob node's contract minus the disk: a chunk is named by its digest and
     * nothing else, and re-storing one is success rather than a conflict.
     */
    /**
     * One node: its own server, its own storage, its own failure state. Separate
     * maps rather than one shared store, because "which node actually has these
     * bytes" is exactly what placement is being tested on — a shared map would
     * make every assertion about location pass regardless.
     */
    private final class BlobNode {

        private final String id;
        private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
        private final Server server;
        private volatile Status failure;

        private BlobNode(String id) {
            this.id = id;
            try {
                server = InProcessServerBuilder.forName(blobGrpcTarget(id).substring("in-process:".length()))
                        .addService(new BlobNodeStub(this))
                        .intercept(recordAuthorization())
                        .build()
                        .start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start blob node stub " + id, e);
            }
        }

        private void reset() {
            blobs.clear();
            failure = null;
        }
    }

    private final class BlobNodeStub extends BlobNodeServiceGrpc.BlobNodeServiceImplBase {

        private final BlobNode node;

        private BlobNodeStub(BlobNode node) {
            this.node = node;
        }

        @Override
        public StreamObserver<PutChunkRequest> putChunk(StreamObserver<PutChunkResponse> responses) {
            String authorization = CALLER_AUTHORIZATION.get();
            return new StreamObserver<>() {

                private String digest;
                private final ByteArrayOutputStream content = new ByteArrayOutputStream();

                @Override
                public void onNext(PutChunkRequest frame) {
                    switch (frame.getPayloadCase()) {
                        case HEADER -> digest = frame.getHeader().getContentHash();
                        case DATA -> content.writeBytes(frame.getData().toByteArray());
                        case PAYLOAD_NOT_SET -> { /* Not something the orchestrator sends. */ }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // The caller gave up mid-send; there is nobody left to answer.
                }

                @Override
                public void onCompleted() {
                    byte[] received = content.toByteArray();
                    blobCalls.add(new BlobCall(node.id, "PutChunk", digest, received, authorization));

                    Status failure = node.failure;
                    if (failure != null) {
                        responses.onError(failure.withDescription("forced blob failure").asRuntimeException());
                        return;
                    }

                    node.blobs.put(digest, received);
                    responses.onNext(PutChunkResponse.newBuilder()
                            .setChunkId(ChunkId.newBuilder().setValue(digest).build())
                            .setContentHash(digest)
                            .setSizeBytes(received.length)
                            .build());
                    responses.onCompleted();
                }
            };
        }

        @Override
        public void getChunk(GetChunkRequest request, StreamObserver<GetChunkResponse> responses) {
            String digest = request.getContentHash();
            blobCalls.add(new BlobCall(node.id, "GetChunk", digest, new byte[0], CALLER_AUTHORIZATION.get()));

            Status failure = node.failure;
            if (failure != null) {
                responses.onError(failure.withDescription("forced blob failure").asRuntimeException());
                return;
            }

            byte[] content = node.blobs.get(digest);
            if (content == null) {
                responses.onError(Status.NOT_FOUND.withDescription("no such blob").asRuntimeException());
                return;
            }
            responses.onNext(GetChunkResponse.newBuilder().setData(ByteString.copyFrom(content)).build());
            responses.onCompleted();
        }

        @Override
        public void chunkExists(ChunkExistsRequest request, StreamObserver<ChunkExistsResponse> responses) {
            String digest = request.getContentHash();
            blobCalls.add(new BlobCall(node.id, "ChunkExists", digest, new byte[0], CALLER_AUTHORIZATION.get()));

            byte[] content = node.blobs.get(digest);
            responses.onNext(ChunkExistsResponse.newBuilder()
                    .setExists(content != null)
                    .setSizeBytes(content == null ? 0 : content.length)
                    .build());
            responses.onCompleted();
        }
    }

    /**
     * Puts the caller's credential where the handlers can record it. Taken from
     * the call's own metadata and carried on its context, so a concurrent call
     * cannot be attributed the wrong token.
     */
    private static ServerInterceptor recordAuthorization() {
        return new ServerInterceptor() {
            @Override
            public <R, S> ServerCall.Listener<R> interceptCall(
                    ServerCall<R, S> call, Metadata headers, ServerCallHandler<R, S> next) {
                Context context = Context.current().withValue(CALLER_AUTHORIZATION, headers.get(AUTHORIZATION));
                return Contexts.interceptCall(context, call, headers, next);
            }
        };
    }

    private void handleMetadata(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        metadataRequests.add(RecordedRequest.from(exchange, body));

        if (metadataFailureStatus != 0) {
            respond(exchange, metadataFailureStatus,
                    "{\"detail\":\"forced metadata failure\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }

        // /api/v1/buckets/{bucket}/objects/{key...}, or the chunk list beside it.
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        String remainder = path.substring("/api/v1/buckets/".length());
        boolean chunksRequest = remainder.contains("/object-chunks/");
        String separator = chunksRequest ? "/object-chunks/" : "/objects/";
        int separatorAt = remainder.indexOf(separator);
        if (separatorAt < 0) {
            respond(exchange, 404, new byte[0]);
            return;
        }
        String bucket = remainder.substring(0, separatorAt);
        String key = remainder.substring(separatorAt + separator.length());

        if (chunksRequest) {
            List<Map<String, Object>> chunks = objectChunks.get(bucket + "/" + key);
            if (chunks == null) {
                respond(exchange, 404, "{\"detail\":\"no such object\"}".getBytes(StandardCharsets.UTF_8));
            } else {
                respondJson(exchange, 200, Map.of("chunks", chunks));
            }
            return;
        }

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
                // Kept verbatim, so a download reads back exactly the list the
                // orchestrator sent rather than one this stub reconstructed.
                objectChunks.put(bucket + "/" + key,
                        (List<Map<String, Object>>) request.getOrDefault("chunks", List.of()));
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

    private static Map<String, Object> chunkRecord(int index, String digest, long sizeBytes) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("index", index);
        record.put("digest", digest);
        record.put("sizeBytes", sizeBytes);
        record.put("nodeId", "blob-node-0");
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

    /**
     * One call to the blob node. {@code content} is empty for the calls that
     * carry none — the digest is what identifies those.
     */
    record BlobCall(String nodeId, String operation, String digest, byte[] content, String authorization) {
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
