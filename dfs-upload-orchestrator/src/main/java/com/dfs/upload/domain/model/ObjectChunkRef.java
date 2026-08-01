package com.dfs.upload.domain.model;

/**
 * One chunk of an object: its position, the digest naming its bytes on a blob
 * node, its size, and which node took it.
 *
 * <p>The node is carried rather than inferred. What the orchestrator records is
 * where it actually wrote, which is not necessarily where a placement rule would
 * say the chunk belongs (ADR 11).
 */
public record ObjectChunkRef(int index, String digest, long sizeBytes, String nodeId) {
}
