package com.dfs.metadata.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Opens a multipart upload. The key, content type and user metadata are fixed
 * here rather than on completion, so completing a session cannot change what
 * the resulting object is called or claims to be.
 *
 * <p>The key travels in the body rather than the path: the upload's own
 * resources live under {@code /multipart-uploads/{uploadId}}, and an object key
 * as a trailing path wildcard would swallow them.
 */
public record InitiateMultipartUploadRequest(
        @NotBlank @Size(max = 1024) String key,
        @Size(max = 255) String contentType,
        @Size(max = 50, message = "must not contain more than 50 entries")
        Map<@NotBlank @Size(max = 128) String, @NotNull @Size(max = 2048) String> userMetadata) {
}
