package com.dfs.upload.domain.exception;

/**
 * The metadata service has no open multipart upload with this id for the
 * caller — never started, already completed or aborted, expired, or another
 * tenant's. It does not say which, by design, and neither does this.
 *
 * <p>The part's bytes are already on the blob nodes by the time this is known,
 * since writes are blob-first (ADR 6); they are unreferenced, and GC reclaims
 * them like any other orphan.
 */
public class UploadNotFoundException extends RuntimeException {

    public UploadNotFoundException(String bucketName, String uploadId) {
        super("No open multipart upload '%s' in bucket '%s'".formatted(uploadId, bucketName));
    }
}
