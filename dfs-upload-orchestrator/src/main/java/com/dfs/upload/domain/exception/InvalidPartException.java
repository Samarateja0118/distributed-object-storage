package com.dfs.upload.domain.exception;

/**
 * The part was refused as the caller's mistake — an empty part, or a part
 * number outside 1 to 10,000 — rather than because anything failed.
 *
 * <p>Distinct from {@link DownstreamServiceException} so that a refusal the
 * caller can fix reaches them as a 400, not as a 502 blaming a dependency that
 * was working correctly.
 */
public class InvalidPartException extends RuntimeException {

    public InvalidPartException(String message) {
        super(message);
    }
}
