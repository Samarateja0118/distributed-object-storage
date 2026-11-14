package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.MetadataValidationException;

/**
 * One uploaded part of a multipart upload: its number and the chunks its bytes
 * were cut into.
 *
 * <p>A part is chunked independently of every other part, so its chunk indexes
 * start again at zero and its last chunk may be short (ADR 12). Its etag is the
 * composite digest of its own chunk list, by the same rule as an object's
 * (ADR 9), which is what lets the client name exactly the content it means when
 * it completes the upload.
 */
public record UploadPart(int partNumber, ObjectChunks chunks) {

    public static final int MIN_PART_NUMBER = 1;

    /** S3's limit, and part of the protocol clients already expect (ADR 12). */
    public static final int MAX_PART_NUMBER = 10_000;

    public UploadPart {
        requireValidPartNumber(partNumber);
        if (chunks == null) {
            throw new MetadataValidationException("A part's chunk list is required");
        }
        // A part exists only as its chunk rows, so an empty one could not be
        // recorded at all. Rejecting it is also what a client would want: a
        // zero-byte part contributes nothing an upload could need.
        if (chunks.chunks().isEmpty()) {
            throw new MetadataValidationException("A part must contain at least one byte");
        }
    }

    public static void requireValidPartNumber(int partNumber) {
        if (partNumber < MIN_PART_NUMBER || partNumber > MAX_PART_NUMBER) {
            throw new MetadataValidationException("Part number must be between %d and %d, was %d"
                    .formatted(MIN_PART_NUMBER, MAX_PART_NUMBER, partNumber));
        }
    }

    public ContentHash etag() {
        return chunks.compositeHash();
    }

    public long sizeBytes() {
        return chunks.totalSizeBytes();
    }
}
