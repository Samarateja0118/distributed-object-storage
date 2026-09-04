package com.dfs.upload.infrastructure.adapter.out.blob;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;

import java.util.Map;

/**
 * The gRPC stubs for each configured blob node, by id.
 *
 * <p>A channel per node rather than one shared channel: they are separate
 * processes on separate hosts, and a channel is where the connection, its
 * backoff, and its failure state live. Sharing one would make an unreachable
 * node look like an unreachable cluster.
 *
 * <p>Two stubs per node because the generated API splits by call style — a
 * blocking stub cannot open a client-streaming call, and the async stub is the
 * wrong shape for reading a single response back.
 */
public record BlobNodeStubs(Map<String, Stubs> byNodeId) {

    public BlobNodeStubs {
        byNodeId = Map.copyOf(byNodeId);
    }

    public Stubs require(String nodeId) {
        Stubs stubs = byNodeId.get(nodeId);
        if (stubs == null) {
            // Reads name the node recorded against the chunk (ADR 11), so an id
            // missing from configuration means bytes that exist and cannot be
            // reached. Saying which id is missing is the whole of the diagnosis.
            throw new UnknownBlobNodeException(nodeId, byNodeId.keySet());
        }
        return stubs;
    }

    public boolean knows(String nodeId) {
        return byNodeId.containsKey(nodeId);
    }

    public record Stubs(
            BlobNodeServiceGrpc.BlobNodeServiceStub async,
            BlobNodeServiceGrpc.BlobNodeServiceBlockingStub blocking) {
    }
}
