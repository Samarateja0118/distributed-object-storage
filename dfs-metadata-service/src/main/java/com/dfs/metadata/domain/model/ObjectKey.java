package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.InvalidObjectKeyException;

/**
 * Path-like identifier of an object within a bucket.
 */
public record ObjectKey(String value) {

    private static final int MAX_LENGTH = 1024;

    public ObjectKey {
        if (value == null || value.isBlank()) {
            throw new InvalidObjectKeyException("Object key must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidObjectKeyException("Object key must not exceed %d characters".formatted(MAX_LENGTH));
        }
        if (value.startsWith("/")) {
            throw new InvalidObjectKeyException("Object key must not start with '/'");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidObjectKeyException("Object key must not contain control characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
