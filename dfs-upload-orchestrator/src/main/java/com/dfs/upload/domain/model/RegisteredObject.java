package com.dfs.upload.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * What the metadata service knows about an object once its bytes are stored:
 * the pointer, not the content.
 *
 * <p>{@code contentHash} is the link between the two halves of the system — it
 * is the name the bytes are filed under on the blob node.
 */
public record RegisteredObject(
        String key,
        UUID versionId,
        long sizeBytes,
        String contentType,
        String contentHash,
        String etag,
        Map<String, String> userMetadata) {
}
