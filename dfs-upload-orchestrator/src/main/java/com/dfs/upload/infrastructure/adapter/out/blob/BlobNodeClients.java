package com.dfs.upload.infrastructure.adapter.out.blob;

import com.dfs.proto.blob.v1.BlobNodeServiceGrpc;
import com.dfs.upload.domain.exception.BlobNodeNotConfiguredException;

import java.util.Map;
import java.util.Set;

/**
 * The gRPC stubs for every configured blob node, by node id.
 *
 * <p>Two stubs per node because the generated API splits by call style: a
 * blocking stub cannot open a client-streaming call, and the async stub is the
 * wrong shape for reading a response back.
 */
public final class BlobNodeClients {

    private final Map<String, Stubs> byNodeId;

    public BlobNodeClients(Map<String, Stubs> byNodeId) {
        this.byNodeId = Map.copyOf(byNodeId);
    }

    /**
     * @throws BlobNodeNotConfiguredException if no node has this id, which on a
     *         read means a chunk is recorded on a node that has since been
     *         removed from the configuration
     */
    public Stubs forNode(String nodeId) {
        Stubs stubs = byNodeId.get(nodeId);
        if (stubs == null) {
            throw new BlobNodeNotConfiguredException(nodeId);
        }
        return stubs;
    }

    public Set<String> nodeIds() {
        return byNodeId.keySet();
    }

    public record Stubs(BlobNodeServiceGrpc.BlobNodeServiceStub async,
                        BlobNodeServiceGrpc.BlobNodeServiceBlockingStub blocking) {
    }
}
