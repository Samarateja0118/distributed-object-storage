package com.dfs.upload.domain.model;

/**
 * One part of a multipart upload, stored and registered (ADR 12).
 *
 * @param etag what the client must name this part by when it completes the
 *             upload: the composite digest of the part's chunks (ADR 9)
 */
public record UploadedPart(int partNumber, String etag, long sizeBytes) {
}
