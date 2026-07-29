package com.dfs.auth.api.rest.dto;

import com.dfs.auth.domain.model.IssuedApiKey;

/**
 * The only response that ever contains the plaintext key. Nothing stores it, so
 * a caller who loses it must issue a new key.
 */
public record IssuedApiKeyResponse(
        ApiKeyResponse apiKey,
        String plaintextKey) {

    public static IssuedApiKeyResponse from(IssuedApiKey issued) {
        return new IssuedApiKeyResponse(ApiKeyResponse.from(issued.apiKey()), issued.plaintextKey());
    }
}
