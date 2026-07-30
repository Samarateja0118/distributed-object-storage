package com.dfs.upload.api.rest.dto;

import com.dfs.upload.domain.model.RegisteredObject;

import java.util.Map;
import java.util.UUID;

public record UploadedObjectResponse(
        String key,
        UUID versionId,
        long sizeBytes,
        String contentType,
        String contentHash,
        String etag,
        Map<String, String> userMetadata) {

    public static UploadedObjectResponse from(RegisteredObject object) {
        return new UploadedObjectResponse(
                object.key(),
                object.versionId(),
                object.sizeBytes(),
                object.contentType(),
                object.contentHash(),
                object.etag(),
                object.userMetadata());
    }
}
