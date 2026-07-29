package com.dfs.auth.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The email is deliberately not {@code @Email}-validated: rejecting a malformed
 * address with a field error would tell an unauthenticated caller more than the
 * uniform "invalid credentials" answer does.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password) {
}
