package com.dfs.metadata.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Registers metadata for content stored elsewhere, so size, digest and chunk
 * list are supplied by the caller rather than computed here.
 *
 * @param contentHash the composite digest of the chunk list (ADR 9), not the
 *                    SHA-256 of the object's bytes
 * @param chunks      the content in order; empty only for a zero-byte object.
 *                    A list that does not agree with {@code contentHash} and
 *                    {@code sizeBytes} is rejected.
 */
public record PutObjectRequest(
        @NotNull @PositiveOrZero Long sizeBytes,
        @Size(max = 255) String contentType,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "must be a lowercase hex SHA-256 digest")
        String contentHash,
        @NotNull List<@Valid ChunkPayload> chunks,
        @Size(max = 50, message = "must not contain more than 50 entries")
        Map<@NotBlank @Size(max = 128) String, @NotNull @Size(max = 2048) String> userMetadata) {
}
