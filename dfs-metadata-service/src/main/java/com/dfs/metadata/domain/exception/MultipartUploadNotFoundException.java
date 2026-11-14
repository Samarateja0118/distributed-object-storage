package com.dfs.metadata.domain.exception;

import com.dfs.common.exception.DfsException;

import java.util.UUID;

/**
 * No open upload with this id in this bucket for this tenant.
 *
 * <p>Deliberately one condition: an id that never existed, one that was
 * completed, aborted or expired, and one belonging to another tenant all report
 * the same way. Distinguishing them would tell a caller which ids are real.
 */
public class MultipartUploadNotFoundException extends DfsException {

    public MultipartUploadNotFoundException(String bucketName, UUID uploadId) {
        super("No open multipart upload '%s' in bucket '%s'".formatted(uploadId, bucketName));
    }
}
