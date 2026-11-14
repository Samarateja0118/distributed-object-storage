package com.dfs.metadata.application.port.out;

import com.dfs.metadata.domain.model.MultipartUpload;
import com.dfs.metadata.domain.model.UploadPart;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for multipart upload sessions and their staged parts (ADR 12).
 *
 * <p>Every lookup takes the bucket as well as the upload id. The bucket is what
 * ties a session to a tenant, so a lookup by id alone would be one forgotten
 * check away from letting an upload id act as a capability.
 */
public interface MultipartUploadRepositoryPort {

    MultipartUpload save(MultipartUpload upload);

    Optional<MultipartUpload> find(UUID uploadId, UUID bucketId);

    /**
     * As {@link #find}, holding a row lock until the transaction ends.
     *
     * <p>Registering a part and completing the upload both take it, so a part
     * cannot be replaced while completion is reading the parts it is about to
     * promote, and a part registered after completion finds no session.
     */
    Optional<MultipartUpload> findForUpdate(UUID uploadId, UUID bucketId);

    /** Replaces whatever was staged under this part number. */
    void replacePart(UUID uploadId, UploadPart part);

    /** Every uploaded part, in part-number order. */
    List<UploadPart> findParts(UUID uploadId);

    /** Deletes the session and, with it, every staged chunk. */
    void delete(UUID uploadId);

    /**
     * Deletes up to {@code limit} sessions that expired at or before {@code now},
     * and returns how many it deleted. Rows another transaction holds are
     * skipped, so concurrent sweeps — one per service instance — divide the work
     * rather than wait on or collide with each other.
     */
    int deleteExpired(Instant now, int limit);
}
