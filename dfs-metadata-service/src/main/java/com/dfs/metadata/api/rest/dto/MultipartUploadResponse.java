package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.MultipartUpload;

import java.time.Instant;
import java.util.UUID;

public record MultipartUploadResponse(
        UUID uploadId,
        String key,
        String contentType,
        Instant createdAt,
        Instant expiresAt) {

    public static MultipartUploadResponse from(MultipartUpload upload) {
        return new MultipartUploadResponse(
                upload.id(), upload.key().value(), upload.contentType(), upload.createdAt(), upload.expiresAt());
    }
}
