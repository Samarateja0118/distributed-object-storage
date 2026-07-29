package com.dfs.auth.api.rest.dto;

import com.dfs.auth.domain.model.AccessToken;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt) {

    public static TokenResponse from(AccessToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresAt());
    }
}
