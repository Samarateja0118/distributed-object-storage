package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.application.port.out.ObjectChunkRepositoryPort;
import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.ObjectChunk;
import com.dfs.metadata.domain.model.ObjectChunks;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class ObjectChunkPersistenceAdapter implements ObjectChunkRepositoryPort {

    private final ObjectChunkJpaRepository repository;

    ObjectChunkPersistenceAdapter(ObjectChunkJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Replaces the whole list rather than diffing it. An object version's content
     * is written once, and overwriting a key without versioning replaces every
     * chunk; a partial update has no meaning here, and deleting first is what
     * keeps a shorter new list from leaving the tail of the old one behind.
     */
    @Override
    public void replaceChunks(UUID objectId, ObjectChunks chunks) {
        repository.deleteByObjectId(objectId);
        List<ObjectChunkEntity> entities = chunks.chunks().stream()
                .map(chunk -> new ObjectChunkEntity(
                        objectId,
                        chunk.index(),
                        chunk.digest().value(),
                        chunk.sizeBytes(),
                        chunk.nodeId()))
                .toList();
        repository.saveAll(entities);
    }

    @Override
    public ObjectChunks findChunks(UUID objectId) {
        return new ObjectChunks(repository.findByObjectIdOrderByChunkIndexAsc(objectId).stream()
                .map(entity -> new ObjectChunk(
                        entity.getChunkIndex(),
                        new ContentHash(entity.getChunkDigest()),
                        entity.getSizeBytes(),
                        entity.getNodeId()))
                .toList());
    }
}
