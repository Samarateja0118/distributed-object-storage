package com.dfs.metadata.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateObjectMetadataRequest(
        @NotNull
        @Size(max = 50, message = "must not contain more than 50 entries")
        Map<@NotBlank @Size(max = 128) String, @NotNull @Size(max = 2048) String> userMetadata) {
}
