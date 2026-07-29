package com.dfs.metadata.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate root describing one version of a stored object.
 * Phase 1 records metadata only; chunk placement arrives in Phase 2.
 */
public record StorageObject(
        UUID id,
        UUID bucketId,
        ObjectKey key,
        UUID versionId,
        boolean latest,
        long sizeBytes,
        String contentType,
        ContentHash contentHash,
        String etag,
        Map<String, String> userMetadata,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public StorageObject {
        userMetadata = userMetadata == null ? Map.of() : Map.copyOf(userMetadata);
    }

    public static StorageObject create(
            UUID bucketId,
            ObjectKey key,
            long sizeBytes,
            String contentType,
            ContentHash contentHash,
            Map<String, String> userMetadata,
            Instant now) {
        return new StorageObject(
                UUID.randomUUID(),
                bucketId,
                key,
                UUID.randomUUID(),
                true,
                sizeBytes,
                contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType,
                contentHash,
                contentHash.toEtag(),
                userMetadata,
                now,
                now,
                null);
    }

    /**
     * Overwrite in place, used when the owning bucket has versioning disabled.
     * A fresh version id is still issued so clients can detect the change.
     */
    public StorageObject replaceContent(
            long newSizeBytes,
            String newContentType,
            ContentHash newContentHash,
            Map<String, String> newUserMetadata,
            Instant now) {
        return new StorageObject(
                id,
                bucketId,
                key,
                UUID.randomUUID(),
                true,
                newSizeBytes,
                newContentType == null || newContentType.isBlank() ? DEFAULT_CONTENT_TYPE : newContentType,
                newContentHash,
                newContentHash.toEtag(),
                newUserMetadata,
                createdAt,
                now,
                null);
    }

    public StorageObject withUserMetadata(Map<String, String> newUserMetadata, Instant now) {
        return new StorageObject(id, bucketId, key, versionId, latest, sizeBytes, contentType, contentHash, etag,
                newUserMetadata, createdAt, now, deletedAt);
    }

}
