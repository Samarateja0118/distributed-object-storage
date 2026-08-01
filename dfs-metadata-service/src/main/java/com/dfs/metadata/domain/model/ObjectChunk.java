package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.MetadataValidationException;

/**
 * One chunk of an object's content: where it sits in the order, what its bytes
 * hash to, and which node holds them (ADR 8).
 *
 * <p>{@code nodeId} is recorded rather than derived. Deriving a location from
 * the digest would mean that changing the node set changes where existing chunks
 * are said to be, while the bytes stay where they were written (ADR 11).
 */
public record ObjectChunk(int index, ContentHash digest, long sizeBytes, String nodeId) {

    public static final int MAX_NODE_ID_LENGTH = 64;

    public ObjectChunk {
        if (index < 0) {
            throw new MetadataValidationException("Chunk index must not be negative");
        }
        if (digest == null) {
            throw new MetadataValidationException("Chunk digest is required");
        }
        if (sizeBytes < 0) {
            throw new MetadataValidationException("Chunk size must not be negative");
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new MetadataValidationException("Chunk node id is required");
        }
        if (nodeId.length() > MAX_NODE_ID_LENGTH) {
            throw new MetadataValidationException(
                    "Chunk node id must not exceed " + MAX_NODE_ID_LENGTH + " characters");
        }
    }
}
