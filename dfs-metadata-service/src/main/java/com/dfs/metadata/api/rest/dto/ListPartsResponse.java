package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.application.port.in.MultipartUploadUseCase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The parts uploaded so far, which is what a client needs to resume an
 * interrupted upload: which numbers are done, and the etags to complete with.
 */
public record ListPartsResponse(UUID uploadId, String key, Instant expiresAt, List<PartResponse> parts) {

    public static ListPartsResponse from(MultipartUploadUseCase.PartListing listing) {
        return new ListPartsResponse(
                listing.upload().id(),
                listing.upload().key().value(),
                listing.upload().expiresAt(),
                listing.parts().stream().map(PartResponse::from).toList());
    }
}
