package com.dfs.upload.domain.exception;

/**
 * A chunk is recorded on a blob node this orchestrator has no address for.
 *
 * <p>Reads follow the node recorded against each chunk rather than recomputing
 * one (ADR 11), so dropping a node from the configuration does not move its
 * chunks anywhere — it makes them unreachable from this process. That is a
 * deployment error rather than a missing object or a failing dependency, and it
 * is reported as one so it is not mistaken for either.
 */
public class BlobNodeNotConfiguredException extends RuntimeException {

    private final String nodeId;

    public BlobNodeNotConfiguredException(String nodeId) {
        super("A chunk is recorded on blob node '%s', which is not in this orchestrator's configured node set"
                .formatted(nodeId));
        this.nodeId = nodeId;
    }

    public String nodeId() {
        return nodeId;
    }
}
