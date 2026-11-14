package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.UploadPart;

/**
 * @param etag what the client must name this part by when it completes the
 *             upload: the composite digest of the part's chunks
 */
public record PartResponse(int partNumber, String etag, long sizeBytes) {

    public static PartResponse from(UploadPart part) {
        return new PartResponse(part.partNumber(), part.etag().value(), part.sizeBytes());
    }
}
