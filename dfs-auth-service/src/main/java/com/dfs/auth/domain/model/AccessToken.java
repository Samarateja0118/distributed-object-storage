package com.dfs.auth.domain.model;

import java.time.Instant;

/**
 * A signed bearer token and the moment it stops being valid.
 */
public record AccessToken(String value, Instant expiresAt) {
}
