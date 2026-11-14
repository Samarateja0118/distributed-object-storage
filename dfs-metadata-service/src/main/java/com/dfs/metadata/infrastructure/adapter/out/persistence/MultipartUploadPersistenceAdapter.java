package com.dfs.metadata.infrastructure.adapter.out.persistence;

import com.dfs.metadata.application.port.out.MultipartUploadRepositoryPort;
import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.MultipartUpload;
import com.dfs.metadata.domain.model.ObjectChunk;
import com.dfs.metadata.domain.model.ObjectChunks;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.UploadPart;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class MultipartUploadPersistenceAdapter implements MultipartUploadRepositoryPort {

    private final MultipartUploadJpaRepository uploads;
    private final MultipartUploadChunkJpaRepository chunks;

    MultipartUploadPersistenceAdapter(MultipartUploadJpaRepository uploads, MultipartUploadChunkJpaRepository chunks) {
        this.uploads = uploads;
        this.chunks = chunks;
    }

    @Override
    public MultipartUpload save(MultipartUpload upload) {
        return toDomain(uploads.save(new MultipartUploadEntity(
                upload.id(),
                upload.bucketId(),
                upload.key().value(),
                upload.contentType(),
                upload.userMetadata(),
                upload.createdAt(),
                upload.expiresAt())));
    }

    @Override
    public Optional<MultipartUpload> find(UUID uploadId, UUID bucketId) {
        return uploads.findByIdAndBucketId(uploadId, bucketId).map(MultipartUploadPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<MultipartUpload> findForUpdate(UUID uploadId, UUID bucketId) {
        return uploads.findByIdAndBucketIdForUpdate(uploadId, bucketId)
                .map(MultipartUploadPersistenceAdapter::toDomain);
    }

    /**
     * Delete then insert, as for an object's chunk list: a re-uploaded part may
     * be cut into fewer chunks than before, and deleting first is what keeps the
     * old tail from staying attached to the new part.
     */
    @Override
    public void replacePart(UUID uploadId, UploadPart part) {
        chunks.deletePart(uploadId, part.partNumber());
        chunks.saveAll(part.chunks().chunks().stream()
                .map(chunk -> new MultipartUploadChunkEntity(
                        uploadId,
                        part.partNumber(),
                        chunk.index(),
                        chunk.digest().value(),
                        chunk.sizeBytes(),
                        chunk.nodeId()))
                .toList());
    }

    @Override
    public List<UploadPart> findParts(UUID uploadId) {
        Map<Integer, List<ObjectChunk>> byPart = new LinkedHashMap<>();
        for (MultipartUploadChunkEntity entity : chunks.findByUploadIdOrderByPartNumberAscChunkIndexAsc(uploadId)) {
            byPart.computeIfAbsent(entity.getPartNumber(), number -> new ArrayList<>()).add(new ObjectChunk(
                    entity.getChunkIndex(),
                    new ContentHash(entity.getChunkDigest()),
                    entity.getSizeBytes(),
                    entity.getNodeId()));
        }
        return byPart.entrySet().stream()
                .map(entry -> new UploadPart(entry.getKey(), new ObjectChunks(entry.getValue())))
                .toList();
    }

    @Override
    public void delete(UUID uploadId) {
        uploads.deleteSession(uploadId);
    }

    @Override
    public int deleteExpired(Instant now, int limit) {
        return uploads.deleteExpired(now, limit);
    }

    private static MultipartUpload toDomain(MultipartUploadEntity entity) {
        return new MultipartUpload(
                entity.getId(),
                entity.getBucketId(),
                new ObjectKey(entity.getObjectKey()),
                entity.getContentType(),
                entity.getUserMetadata(),
                entity.getCreatedAt(),
                entity.getExpiresAt());
    }
}
