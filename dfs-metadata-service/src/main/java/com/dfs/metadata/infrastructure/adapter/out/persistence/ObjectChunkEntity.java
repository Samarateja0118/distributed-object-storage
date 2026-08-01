package com.dfs.metadata.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "object_chunks")
@IdClass(ObjectChunkEntity.Key.class)
public class ObjectChunkEntity {

    @Id
    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_digest", nullable = false, length = 64)
    private String chunkDigest;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    protected ObjectChunkEntity() {
    }

    ObjectChunkEntity(UUID objectId, int chunkIndex, String chunkDigest, long sizeBytes, String nodeId) {
        this.objectId = objectId;
        this.chunkIndex = chunkIndex;
        this.chunkDigest = chunkDigest;
        this.sizeBytes = sizeBytes;
        this.nodeId = nodeId;
    }

    UUID getObjectId() {
        return objectId;
    }

    int getChunkIndex() {
        return chunkIndex;
    }

    String getChunkDigest() {
        return chunkDigest;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getNodeId() {
        return nodeId;
    }

    /** Composite primary key: a chunk is identified by its object and position. */
    static class Key implements Serializable {

        private UUID objectId;
        private int chunkIndex;

        protected Key() {
        }

        Key(UUID objectId, int chunkIndex) {
            this.objectId = objectId;
            this.chunkIndex = chunkIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key && chunkIndex == key.chunkIndex && Objects.equals(objectId, key.objectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectId, chunkIndex);
        }
    }
}
