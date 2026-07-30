package com.dfs.blob.domain.exception;

import com.dfs.blob.domain.model.BlobDigest;

/**
 * The bytes received did not hash to the digest they were stored under.
 *
 * <p>Treated as a client error, not a server one: the request itself was
 * self-inconsistent. Storing it anyway would poison the store permanently, since
 * a later honest write of those same bytes would find the name already taken and
 * be served the wrong content.
 */
public class BlobDigestMismatchException extends BlobValidationException {

    public BlobDigestMismatchException(BlobDigest expected, String actual) {
        super("Content hashes to '%s' but was sent as '%s'".formatted(actual, expected));
    }
}
