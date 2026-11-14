package com.dfs.metadata.application.service;

import com.dfs.metadata.application.port.in.MultipartUploadUseCase;
import com.dfs.metadata.application.port.out.BucketRepositoryPort;
import com.dfs.metadata.application.port.out.MultipartUploadRepositoryPort;
import com.dfs.metadata.domain.exception.BucketNotFoundException;
import com.dfs.metadata.domain.exception.MultipartUploadNotFoundException;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;
import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.MultipartAssembly;
import com.dfs.metadata.domain.model.MultipartUpload;
import com.dfs.metadata.domain.model.ObjectChunks;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;
import com.dfs.metadata.domain.model.UploadPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The session half of multipart upload (ADR 12). The bytes of each part are
 * already on blob nodes by the time it is registered here, written by the
 * orchestrator blob-first as for any upload (ADR 6).
 *
 * <p>Every operation resolves the bucket from the caller's tenant first and
 * looks the upload up within it, so the tenant always comes from the token and
 * an upload id never stands in for it.
 */
@Service
@Transactional
public class MultipartUploadService implements MultipartUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadService.class);

    private final MultipartUploadRepositoryPort uploads;
    private final BucketRepositoryPort buckets;
    private final ObjectVersionWriter versionWriter;
    private final MultipartPolicy policy;
    private final Clock clock;

    public MultipartUploadService(
            MultipartUploadRepositoryPort uploads,
            BucketRepositoryPort buckets,
            ObjectVersionWriter versionWriter,
            MultipartPolicy policy,
            Clock clock) {
        this.uploads = uploads;
        this.buckets = buckets;
        this.versionWriter = versionWriter;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public MultipartUpload initiate(InitiateCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), command.bucketName());
        return uploads.save(MultipartUpload.initiate(
                bucket.id(),
                new ObjectKey(command.objectKey()),
                command.contentType(),
                command.userMetadata(),
                Instant.now(clock),
                policy.sessionTtl()));
    }

    /**
     * Stages a part, replacing any earlier upload of the same number.
     *
     * <p>The chunk list is checked against the part's declared hash and size, as
     * a single upload's is, before anything is written. The session row is held
     * for update, so a part cannot be swapped while completion is reading it.
     */
    @Override
    public UploadPart registerPart(RegisterPartCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), command.bucketName());
        ObjectChunks chunks = new ObjectChunks(command.chunks());
        chunks.requireConsistentWith(new ContentHash(command.contentHash()), command.sizeBytes());
        UploadPart part = new UploadPart(command.partNumber(), chunks);

        requireOpenUpload(bucket, command.uploadId(), true);
        uploads.replacePart(command.uploadId(), part);
        return part;
    }

    @Override
    @Transactional(readOnly = true)
    public PartListing listParts(UUID tenantId, String bucketName, UUID uploadId) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        MultipartUpload upload = requireOpenUpload(bucket, uploadId, false);
        return new PartListing(upload, uploads.findParts(uploadId));
    }

    /**
     * Promotes the named parts into an object and ends the session, in one
     * transaction: the object, its chunk list, and the removal of the staged
     * rows commit together or not at all (ADR 12).
     *
     * <p>The object is written through the same path as a single PUT, so
     * versioning and overwrite behave identically however the content arrived.
     */
    @Override
    public StorageObject complete(CompleteCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), command.bucketName());
        MultipartUpload upload = requireOpenUpload(bucket, command.uploadId(), true);

        List<MultipartAssembly.PartSelection> selected = command.parts() == null ? List.of()
                : command.parts().stream()
                        .map(part -> new MultipartAssembly.PartSelection(
                                part.partNumber(), new ContentHash(part.etag())))
                        .toList();
        ObjectChunks chunks = MultipartAssembly.assemble(
                uploads.findParts(upload.id()), selected, policy.minPartSizeBytes());

        StorageObject object = versionWriter.write(
                bucket,
                upload.key(),
                chunks.totalSizeBytes(),
                upload.contentType(),
                chunks.compositeHash(),
                upload.userMetadata(),
                chunks,
                Instant.now(clock));
        uploads.delete(upload.id());

        log.info("Completed multipart upload {} into {}/{} ({} parts, {} bytes)",
                upload.id(), bucket.name().value(), upload.key().value(), selected.size(), object.sizeBytes());
        return object;
    }

    /**
     * Ends the session without an object. The staged chunks' bytes stay on the
     * blob nodes, now referenced by nothing — the same state a failed metadata
     * write leaves, reclaimed by the same GC (ADR 4).
     */
    @Override
    public void abort(UUID tenantId, String bucketName, UUID uploadId) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        requireOpenUpload(bucket, uploadId, true);
        uploads.delete(uploadId);
    }

    @Override
    public int expireStaleUploads() {
        int expired = uploads.deleteExpired(Instant.now(clock), policy.sweepBatchSize());
        if (expired > 0) {
            log.info("Expired {} abandoned multipart upload(s)", expired);
        }
        return expired;
    }

    /**
     * An expired session is refused here even if the sweep has not yet deleted
     * it, so whether a late request succeeds never depends on when the sweep
     * last ran.
     */
    private MultipartUpload requireOpenUpload(Bucket bucket, UUID uploadId, boolean forUpdate) {
        return (forUpdate ? uploads.findForUpdate(uploadId, bucket.id()) : uploads.find(uploadId, bucket.id()))
                .filter(upload -> !upload.isExpiredAt(Instant.now(clock)))
                .orElseThrow(() -> new MultipartUploadNotFoundException(bucket.name().value(), uploadId));
    }

    private Bucket requireBucket(UUID tenantId, String bucketName) {
        BucketName name = new BucketName(bucketName);
        return buckets.findByTenantAndName(tenantId, name)
                .orElseThrow(() -> new BucketNotFoundException(name.value()));
    }
}
