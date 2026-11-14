package com.dfs.upload.api.rest.dto;

import com.dfs.upload.domain.model.UploadedPart;

/**
 * @param etag what to name this part by when completing the upload with the
 *             metadata service
 */
public record UploadedPartResponse(int partNumber, String etag, long sizeBytes) {

    public static UploadedPartResponse from(UploadedPart part) {
        return new UploadedPartResponse(part.partNumber(), part.etag(), part.sizeBytes());
    }
}
