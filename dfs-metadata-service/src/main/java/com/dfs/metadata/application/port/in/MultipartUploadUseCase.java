package com.dfs.metadata.application.port.in;

import com.dfs.metadata.domain.model.MultipartUpload;
import com.dfs.metadata.domain.model.ObjectChunk;
import com.dfs.metadata.domain.model.StorageObject;
import com.dfs.metadata.domain.model.UploadPart;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port for S3-style multipart uploads (ADR 12): open a session, stage
 * parts into it, then promote them into an object or abandon them.
 *
 * <p>As with single uploads, byte storage is out of scope: each part arrives as
 * the chunk list the orchestrator already wrote, and is checked against the
 * part's declared hash and size before it is staged.
 */
public interface MultipartUploadUseCase {

    MultipartUpload initiate(InitiateCommand command);

    UploadPart registerPart(RegisterPartCommand command);

    PartListing listParts(UUID tenantId, String bucketName, UUID uploadId);

    StorageObject complete(CompleteCommand command);

    void abort(UUID tenantId, String bucketName, UUID uploadId);

    /**
     * Deletes one batch of expired sessions and returns how many went. Without
     * this an abandoned upload's staged rows would stay forever, holding chunks
     * GC can never classify (ADR 12).
     */
    int expireStaleUploads();

    record InitiateCommand(
            UUID tenantId,
            String bucketName,
            String objectKey,
            String contentType,
            Map<String, String> userMetadata) {
    }

    /**
     * @param contentHash the composite digest of {@code chunks} — the part's etag
     */
    record RegisterPartCommand(
            UUID tenantId,
            String bucketName,
            UUID uploadId,
            int partNumber,
            long sizeBytes,
            String contentHash,
            List<ObjectChunk> chunks) {
    }

    record CompleteCommand(
            UUID tenantId,
            String bucketName,
            UUID uploadId,
            List<SelectedPart> parts) {
    }

    record SelectedPart(int partNumber, String etag) {
    }

    record PartListing(MultipartUpload upload, List<UploadPart> parts) {
    }
}
