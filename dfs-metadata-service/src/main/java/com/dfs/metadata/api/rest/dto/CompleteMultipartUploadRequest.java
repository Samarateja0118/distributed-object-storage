package com.dfs.metadata.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The parts to make the object from, in ascending part-number order, each
 * named with the etag its upload returned.
 */
public record CompleteMultipartUploadRequest(
        @NotEmpty @Size(max = 10_000) List<@NotNull @Valid CompletedPart> parts) {

    public record CompletedPart(
            @NotNull Integer partNumber,
            @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "must be a lowercase hex SHA-256 digest")
            String etag) {
    }
}
