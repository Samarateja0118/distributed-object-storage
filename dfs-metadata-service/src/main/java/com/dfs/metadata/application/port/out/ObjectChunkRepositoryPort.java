package com.dfs.metadata.application.port.out;

import com.dfs.metadata.domain.model.ObjectChunks;

import java.util.UUID;

/**
 * Outbound port for an object's chunk list (ADR 8).
 *
 * <p>Separate from {@code ObjectMetadataRepositoryPort} on purpose: the chunk
 * list is the one part of an object that grows with its size, so listing a
 * thousand objects must not drag their chunks along. Only the paths that need
 * the bytes ask for it.
 */
public interface ObjectChunkRepositoryPort {

    void replaceChunks(UUID objectId, ObjectChunks chunks);

    ObjectChunks findChunks(UUID objectId);
}
