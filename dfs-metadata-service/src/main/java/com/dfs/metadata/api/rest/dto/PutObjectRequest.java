package com.dfs.metadata.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Phase 1 registers metadata for content stored elsewhere, so size and digest
 * are supplied by the caller rather than computed here.
 */
public record PutObjectRequest(
        @NotNull @PositiveOrZero Long sizeBytes,
        @Size(max = 255) String contentType,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "must be a lowercase hex SHA-256 digest")
        String contentHash,
        @Size(max = 50, message = "must not contain more than 50 entries")
        Map<@NotBlank @Size(max = 128) String, @NotNull @Size(max = 2048) String> userMetadata) {
}
