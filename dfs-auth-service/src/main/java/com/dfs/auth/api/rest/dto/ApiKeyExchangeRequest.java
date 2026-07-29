package com.dfs.auth.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyExchangeRequest(
        @NotBlank @Size(max = 256) String apiKey) {
}
