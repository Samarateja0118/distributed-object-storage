package com.dfs.upload.infrastructure.adapter.out.metadata;

import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.RegisteredObject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wire shape of the metadata service's object API.
 *
 * <p>Kept separate from {@link RegisteredObject} so that service's REST contract
 * can gain fields without those fields leaking into this one's domain.
 */
final class MetadataObjectPayloads {

    private MetadataObjectPayloads() {
    }

    record PutObjectRequest(
            long sizeBytes,
            String contentType,
            String contentHash,
            List<ChunkPayload> chunks,
            Map<String, String> userMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChunkPayload(int index, String digest, long sizeBytes, String nodeId) {

        static ChunkPayload from(ObjectChunkRef chunk) {
            return new ChunkPayload(chunk.index(), chunk.digest(), chunk.sizeBytes(), chunk.nodeId());
        }

        ObjectChunkRef toDomain() {
            return new ObjectChunkRef(index, digest, sizeBytes, nodeId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ObjectChunksResponse(List<ChunkPayload> chunks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ObjectResponse(
            String key,
            UUID versionId,
            long sizeBytes,
            String contentType,
            String contentHash,
            String etag,
            Map<String, String> userMetadata) {

        RegisteredObject toDomain() {
            return new RegisteredObject(
                    key,
                    versionId,
                    sizeBytes,
                    contentType,
                    contentHash,
                    etag,
                    userMetadata == null ? Map.of() : userMetadata);
        }
    }
}
