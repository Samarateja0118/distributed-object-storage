package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.InvalidContentHashException;

import java.util.regex.Pattern;

/**
 * A lowercase hex SHA-256 digest, used both for a chunk's own content and for
 * the composite naming a whole object.
 *
 * <p>The two share a shape and are not the same thing. A chunk's digest is the
 * hash of its bytes; an object's is the hash of its ordered chunk digests
 * (ADR 9), so it does not match what a client gets by hashing the file locally.
 */
public record ContentHash(String value) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    public ContentHash {
        if (value == null || !SHA256_HEX.matcher(value).matches()) {
            throw new InvalidContentHashException("Content hash must be a lowercase hex SHA-256 digest");
        }
    }

    /**
     * The digest doubles as the entity tag. For a chunked object that tag is the
     * composite, which stays a stable identity for the content without being the
     * hash a client would compute over the file.
     */
    public String toEtag() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
