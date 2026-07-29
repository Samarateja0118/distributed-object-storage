package com.dfs.metadata.api.rest.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBucketRequest(
        @NotNull Boolean versioningEnabled) {
}
