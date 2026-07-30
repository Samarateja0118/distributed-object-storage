package com.dfs.blob.domain.model;

import com.dfs.blob.domain.exception.InvalidBlobDigestException;
import com.dfs.common.hash.Sha256;

/**
 * A blob's name: the lowercase hex SHA-256 of its own bytes.
 *
 * <p>Content addressing makes stored blobs immutable and deduplicated for free —
 * two callers storing identical bytes converge on one file, and a blob can never
 * be modified in place because different bytes would have a different name.
 *
 * <p>The digest arrives in a URL path, so validating it here is also what keeps
 * a crafted value like {@code ../../etc/passwd} from ever reaching the
 * filesystem.
 */
public record BlobDigest(String value) {

    public BlobDigest {
        if (!Sha256.isValidHex(value)) {
            throw new InvalidBlobDigestException("Blob digest must be a lowercase hex SHA-256 digest");
        }
    }

    /**
     * First fan-out directory. A flat directory would hold millions of entries on
     * a full node, which makes listing and some filesystems' lookups crawl.
     */
    public String firstShard() {
        return value.substring(0, 2);
    }

    public String secondShard() {
        return value.substring(2, 4);
    }

    @Override
    public String toString() {
        return value;
    }
}
