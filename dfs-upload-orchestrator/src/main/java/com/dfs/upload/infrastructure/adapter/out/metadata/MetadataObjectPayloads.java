package com.dfs.upload.infrastructure.adapter.out.metadata;

import com.dfs.upload.domain.model.RegisteredObject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
            Map<String, String> userMetadata) {
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
