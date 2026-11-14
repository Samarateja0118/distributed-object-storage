package com.dfs.metadata.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Stages one part's chunk list, already written to blob nodes.
 *
 * @param contentHash the composite digest of {@code chunks}, which becomes the
 *                    part's etag; rejected if it does not match the list
 * @param chunks      the part's content in order, indexed from zero within the
 *                    part. Never empty: a part with no bytes cannot be staged.
 */
public record PutPartRequest(
        @NotNull @Positive Long sizeBytes,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "must be a lowercase hex SHA-256 digest")
        String contentHash,
        @NotEmpty List<@Valid ChunkPayload> chunks) {
}
