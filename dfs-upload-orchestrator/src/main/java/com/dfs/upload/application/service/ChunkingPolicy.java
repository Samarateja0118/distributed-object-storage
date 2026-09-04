package com.dfs.upload.application.service;

/**
 * How an object is cut into chunks, and where they go.
 *
 * <p>Plain values rather than the configuration record itself, so the service
 * doing the chunking depends on the two numbers it needs and not on how the
 * process happens to be configured.
 *
 * @param chunkSizeBytes    size of every chunk but the last. Part of the
 *                          contract, not a free tuning knob: the same bytes cut
 *                          at a different size produce a different composite
 *                          digest, so changing it changes the identity of every
 *                          object written afterwards (ADR 9).
 * @param maxObjectSizeBytes ceiling on a single upload. Once content is streamed
 *                          chunk by chunk this is a policy limit rather than the
 *                          heap bound it was in Phase 1.
 */
public record ChunkingPolicy(int chunkSizeBytes, long maxObjectSizeBytes) {
}
