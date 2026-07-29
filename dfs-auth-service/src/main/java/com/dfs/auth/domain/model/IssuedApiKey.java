package com.dfs.auth.domain.model;

/**
 * Pairs a freshly created key with its plaintext secret. This is the only moment
 * the secret exists outside the caller's hands — nothing persists it, and the
 * REST layer returns it exactly once.
 */
public record IssuedApiKey(ApiKey apiKey, String plaintextKey) {
}
