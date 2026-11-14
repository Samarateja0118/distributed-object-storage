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
@Table(name = "multipart_upload_chunks")
@IdClass(MultipartUploadChunkEntity.Key.class)
public class MultipartUploadChunkEntity {

    @Id
    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Id
    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_digest", nullable = false, length = 64)
    private String chunkDigest;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    protected MultipartUploadChunkEntity() {
    }

    MultipartUploadChunkEntity(UUID uploadId, int partNumber, int chunkIndex, String chunkDigest,
                               long sizeBytes, String nodeId) {
        this.uploadId = uploadId;
        this.partNumber = partNumber;
        this.chunkIndex = chunkIndex;
        this.chunkDigest = chunkDigest;
        this.sizeBytes = sizeBytes;
        this.nodeId = nodeId;
    }

    UUID getUploadId() {
        return uploadId;
    }

    int getPartNumber() {
        return partNumber;
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

    /** Composite primary key: a staged chunk is identified by its upload, part and position. */
    static class Key implements Serializable {

        private UUID uploadId;
        private int partNumber;
        private int chunkIndex;

        protected Key() {
        }

        Key(UUID uploadId, int partNumber, int chunkIndex) {
            this.uploadId = uploadId;
            this.partNumber = partNumber;
            this.chunkIndex = chunkIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && partNumber == key.partNumber
                    && chunkIndex == key.chunkIndex
                    && Objects.equals(uploadId, key.uploadId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uploadId, partNumber, chunkIndex);
        }
    }
}
