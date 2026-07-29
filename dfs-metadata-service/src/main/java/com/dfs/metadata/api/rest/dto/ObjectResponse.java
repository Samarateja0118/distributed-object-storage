package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.StorageObject;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ObjectResponse(
        UUID id,
        String key,
        UUID versionId,
        boolean latest,
        long sizeBytes,
        String contentType,
        String contentHash,
        String etag,
        Map<String, String> userMetadata,
        Instant createdAt,
        Instant updatedAt) {

    public static ObjectResponse from(StorageObject object) {
        return new ObjectResponse(
                object.id(),
                object.key().value(),
                object.versionId(),
                object.latest(),
                object.sizeBytes(),
                object.contentType(),
                object.contentHash().value(),
                object.etag(),
                object.userMetadata(),
                object.createdAt(),
                object.updatedAt());
    }
}
