package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.InvalidContentHashException;

import java.util.regex.Pattern;

/**
 * Lowercase hex SHA-256 digest of an object's full content.
 */
public record ContentHash(String value) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    public ContentHash {
        if (value == null || !SHA256_HEX.matcher(value).matches()) {
            throw new InvalidContentHashException("Content hash must be a lowercase hex SHA-256 digest");
        }
    }

    /**
     * Single-part objects use the content digest directly as their entity tag,
     * matching S3 semantics for non-multipart uploads.
     */
    public String toEtag() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
