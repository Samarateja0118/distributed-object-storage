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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the blob nodes and the metadata service, each on the transport it
 * really uses: gRPC for the byte path, HTTP for metadata.
 *
 * <p>Two blob nodes, {@link #NODE_IDS}, each a separate in-process gRPC server
 * with its own storage, so placement and fallback are tested against the same
 * per-node channels production builds. Every blob call is recorded with the node
 * that received it.
 *
 * <p>Real servers rather than mocked clients on purpose: what these tests check
 * is the wire contract — the digest and frames the node receives, the forwarded
 * {@code Authorization}, the JSON field names. A mock would be configured with
 * this service's own assumptions and would agree with them whether or not they
 * were right.
 *
 * <p>Each half keeps enough state to behave like the real thing, so a round trip
 * can be tested end to end. {@link #failBlobWith}, {@link #failBlobNodeWith} and
 * {@link #failMetadataWith} force the failure cases.
 */
final class DownstreamStub {

    /** The configured node set. The first is the id every pre-Phase-2 chunk is recorded on. */
    static final List<String> NODE_IDS = List.of("blob-node-0", "blob-node-1");

    /** In-process: needs no port, and leaves nothing to clean up between runs. */
    private static final String BLOB_GRPC_NAME_PREFIX = "upload-blob-node-stub-";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> CALLER_AUTHORIZATION = Context.key("stub-authorization");

    private final HttpServer metadataServer;

    /** By node id, in {@link #NODE_IDS} order. */
    private final Map<String, BlobNodeStub> blobNodes = new LinkedHashMap<>();

    private final List<BlobCall> blobCalls = Collections.synchronizedList(new ArrayList<>());
    private final List<RecordedRequest> metadataRequests = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Map<String, Object>> objects = new ConcurrentHashMap<>();

    /** Chunk lists, keyed like {@link #objects} and served from their own path. */
    private final Map<String, List<Map<String, Object>>> objectChunks = new ConcurrentHashMap<>();

    /** Registered multipart parts, keyed by bucket/uploadId/partNumber, as sent. */
    private final Map<String, Map<String, Object>> parts = new ConcurrentHashMap<>();

    private volatile int metadataFailureStatus;

    DownstreamStub() {
        try {
            metadataServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            metadataServer.createContext("/api/v1/buckets", this::handleMetadata);
            metadataServer.start();

            for (String nodeId : NODE_IDS) {
                BlobNodeStub node = new BlobNodeStub(nodeId);
                InProcessServerBuilder.forName(BLOB_GRPC_NAME_PREFIX + nodeId)
                        .addService(node)
                        .intercept(recordAuthorization())
                        .build()
                        .start();
                blobNodes.put(nodeId, node);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the downstream stub", e);
        }
    }

    String metadataBaseUrl() {
        return "http://127.0.0.1:" + metadataServer.getAddress().getPort();
    }

    String blobGrpcTarget(String nodeId) {
        return "in-process:" + BLOB_GRPC_NAME_PREFIX + nodeId;
    }

    void reset() {
        blobCalls.clear();
        metadataRequests.clear();
        blobNodes.values().forEach(BlobNodeStub::reset);
        objects.clear();
        objectChunks.clear();
        parts.clear();
        metadataFailureStatus = 0;
    }

    /** Fails every blob call on every node with this status; {@code null} restores success. */
    void failBlobWith(Status status) {
        blobNodes.values().forEach(node -> node.failure = status);
    }

    /**
     * Fails every call to one node. {@link Status#UNAVAILABLE} is how a node that
     * is down looks to the orchestrator; any other status is a node that answered
     * and refused.
     */
    void failBlobNodeWith(String nodeId, Status status) {
        node(nodeId).failure = status;
    }

    void failMetadataWith(int status) {
        metadataFailureStatus = status;
    }

    /** Pre-loads metadata pointing at a chunk the blob node does not have. */
    void registerDanglingObject(String bucket, String key, String digest) {
        objects.put(bucket + "/" + key, objectRecord(key, digest, 12, "text/plain", Map.of()));
        objectChunks.put(bucket + "/" + key, List.of(chunkRecord(0, digest, 12, NODE_IDS.getFirst())));
    }

    /**
     * Pre-loads a one-chunk object recorded on {@code nodeId}, with its bytes
     * stored there if that node exists in this stub. A node id outside
     * {@link #NODE_IDS} stands for a node removed from the configuration.
     */
    void registerObjectOnNode(String bucket, String key, byte[] content, String nodeId) {
        String digest = com.dfs.common.hash.Sha256.hexOfContent(content);
        BlobNodeStub node = blobNodes.get(nodeId);
        if (node != null) {
            node.blobs.put(digest, content);
        }
        objects.put(bucket + "/" + key, objectRecord(key, digest, content.length, "text/plain", Map.of()));
        objectChunks.put(bucket + "/" + key, List.of(chunkRecord(0, digest, content.length, nodeId)));
    }

    /**
     * Pre-loads a complete object whose content type is absent, which the
     * metadata API permits for records not written through the orchestrator.
     */
    void registerObjectWithoutContentType(String bucket, String key, String digest, byte[] content) {
        node(NODE_IDS.getFirst()).blobs.put(digest, content);
        objects.put(bucket + "/" + key, objectRecord(key, digest, content.length, null, Map.of()));
        objectChunks.put(bucket + "/" + key, List.of(chunkRecord(0, digest, content.length, NODE_IDS.getFirst())));
    }

    /** The body the orchestrator registered for this part, or null if none was. */
    Map<String, Object> registeredPart(String bucket, java.util.UUID uploadId, int partNumber) {
        return parts.get(bucket + "/" + uploadId + "/" + partNumber);
    }

    List<BlobCall> blobCalls() {
        return List.copyOf(blobCalls);
    }

    List<RecordedRequest> metadataRequests() {
        return List.copyOf(metadataRequests);
    }

    /** Whether any node holds this digest. */
    boolean hasBlob(String digest) {
        return blobNodes.values().stream().anyMatch(node -> node.blobs.containsKey(digest));
    }

    boolean hasBlobOn(String nodeId, String digest) {
        return node(nodeId).blobs.containsKey(digest);
    }

    private BlobNodeStub node(String nodeId) {
        BlobNodeStub node = blobNodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("No stub blob node " + nodeId);
        }
        return node;
    }

    /**
     * The blob node's contract minus the disk: a chunk is named by its digest and
     * nothing else, and re-storing one is success rather than a conflict.
     */
    private final class BlobNodeStub extends BlobNodeServiceGrpc.BlobNodeServiceImplBase {

        private final String nodeId;
        private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
        private volatile Status failure;

        BlobNodeStub(String nodeId) {
            this.nodeId = nodeId;
        }

        void reset() {
            blobs.clear();
            failure = null;
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
                    blobCalls.add(new BlobCall("PutChunk", digest, received, authorization, nodeId));

                    Status forced = failure;
                    if (forced != null) {
                        responses.onError(forced.withDescription("forced blob failure").asRuntimeException());
                        return;
                    }

                    blobs.put(digest, received);
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
            blobCalls.add(new BlobCall("GetChunk", digest, new byte[0], CALLER_AUTHORIZATION.get(), nodeId));

            Status forced = failure;
            if (forced != null) {
                responses.onError(forced.withDescription("forced blob failure").asRuntimeException());
                return;
            }

            byte[] content = blobs.get(digest);
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
            blobCalls.add(new BlobCall("ChunkExists", digest, new byte[0], CALLER_AUTHORIZATION.get(), nodeId));

            byte[] content = blobs.get(digest);
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

        // /api/v1/buckets/{bucket}/multipart-uploads/{uploadId}/parts/{n}: the
        // one multipart call the orchestrator makes. Any upload id is accepted;
        // failMetadataWith stands in for the session being absent or refusing.
        int multipartAt = remainder.indexOf("/multipart-uploads/");
        if (multipartAt >= 0 && "PUT".equals(exchange.getRequestMethod())) {
            String bucket = remainder.substring(0, multipartAt);
            String[] segments = remainder.substring(multipartAt + "/multipart-uploads/".length()).split("/");
            if (segments.length != 3 || !segments[1].equals("parts")) {
                respond(exchange, 404, new byte[0]);
                return;
            }
            Map<String, Object> request = JSON.readValue(body, Map.class);
            parts.put(bucket + "/" + segments[0] + "/" + segments[2], request);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("partNumber", Integer.parseInt(segments[2]));
            response.put("etag", request.get("contentHash"));
            response.put("sizeBytes", request.get("sizeBytes"));
            respondJson(exchange, 200, response);
            return;
        }
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

    private static Map<String, Object> chunkRecord(int index, String digest, long sizeBytes, String nodeId) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("index", index);
        record.put("digest", digest);
        record.put("sizeBytes", sizeBytes);
        record.put("nodeId", nodeId);
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
     * One call to a blob node. {@code content} is empty for the calls that carry
     * none — the digest is what identifies those. {@code nodeId} is the node that
     * received the call.
     */
    record BlobCall(String operation, String digest, byte[] content, String authorization, String nodeId) {
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
