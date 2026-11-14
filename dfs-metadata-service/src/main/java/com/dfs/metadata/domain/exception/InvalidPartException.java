package com.dfs.metadata.domain.exception;

/**
 * A multipart completion names parts that cannot make an object: missing,
 * out of order, too small, or not the content the client thinks it is.
 *
 * <p>A validation failure like any other, so it maps to 400; its own type keeps
 * the problem detail specific enough for a client to tell which part to fix.
 */
public class InvalidPartException extends MetadataValidationException {

    public InvalidPartException(String message) {
        super(message);
    }
}
