package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.MetadataValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An open multipart upload session (ADR 12): the key it will become, the
 * attributes the finished object will carry, and when the session lapses.
 *
 * <p>The object's attributes are fixed when the session starts rather than when
 * it completes, as in S3. Completion supplies only the list of parts, so a
 * client that completes an upload it did not start cannot change what the
 * object is called or what it claims to be.
 *
 * <p>The id names the session; it does not authorize anything. Every lookup is
 * scoped to a bucket the caller's token owns, so possessing another tenant's
 * upload id gets a caller no further than a guess would.
 */
public record MultipartUpload(
        UUID id,
        UUID bucketId,
        ObjectKey key,
        String contentType,
        Map<String, String> userMetadata,
        Instant createdAt,
        Instant expiresAt
) {

    public MultipartUpload {
        userMetadata = userMetadata == null ? Map.of() : Map.copyOf(userMetadata);
        if (!expiresAt.isAfter(createdAt)) {
            throw new MetadataValidationException("A multipart upload must expire after it is created");
        }
    }

    public static MultipartUpload initiate(
            UUID bucketId,
            ObjectKey key,
            String contentType,
            Map<String, String> userMetadata,
            Instant now,
            Duration ttl) {
        return new MultipartUpload(
                UUID.randomUUID(),
                bucketId,
                key,
                contentType == null || contentType.isBlank() ? StorageObject.DEFAULT_CONTENT_TYPE : contentType,
                userMetadata,
                now,
                now.plus(ttl));
    }

    /**
     * Whether the session has lapsed. An expired session is treated as absent
     * from the moment it expires, not from the moment the sweep reaches it, so
     * the sweep's schedule never decides whether a late part is accepted.
     */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
