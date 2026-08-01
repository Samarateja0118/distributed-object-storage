package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.ObjectChunk;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One chunk of an object's content, as it crosses the API in either direction.
 */
public record ChunkPayload(
        @NotNull @PositiveOrZero Integer index,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "must be a lowercase hex SHA-256 digest")
        String digest,
        @NotNull @PositiveOrZero Long sizeBytes,
        @NotBlank @Size(max = 64) String nodeId) {

    public static ChunkPayload from(ObjectChunk chunk) {
        return new ChunkPayload(chunk.index(), chunk.digest().value(), chunk.sizeBytes(), chunk.nodeId());
    }

    public ObjectChunk toDomain() {
        return new ObjectChunk(index, new ContentHash(digest), sizeBytes, nodeId);
    }
}
