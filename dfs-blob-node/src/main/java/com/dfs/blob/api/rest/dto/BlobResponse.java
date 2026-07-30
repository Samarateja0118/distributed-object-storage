package com.dfs.blob.api.rest.dto;

import com.dfs.blob.domain.model.StoredBlob;

public record BlobResponse(String digest, long sizeBytes) {

    public static BlobResponse from(StoredBlob blob) {
        return new BlobResponse(blob.digest().value(), blob.sizeBytes());
    }
}
