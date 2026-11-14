package com.dfs.metadata.application.service;

import com.dfs.metadata.application.port.out.ObjectChunkRepositoryPort;
import com.dfs.metadata.application.port.out.ObjectMetadataRepositoryPort;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.ObjectChunks;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Writes a new version of an object together with its chunk list.
 *
 * <p>One place for it because there are two ways for an object to come into
 * being — a single PUT and a completed multipart upload — and they must not
 * disagree about versioning. Both arrive here with a chunk list already checked
 * against the content hash and size.
 *
 * <p>Runs in the caller's transaction, so an object never becomes visible
 * without the chunk list saying where its content is.
 */
@Component
class ObjectVersionWriter {

    private final ObjectMetadataRepositoryPort objectRepository;
    private final ObjectChunkRepositoryPort chunkRepository;

    ObjectVersionWriter(ObjectMetadataRepositoryPort objectRepository, ObjectChunkRepositoryPort chunkRepository) {
        this.objectRepository = objectRepository;
        this.chunkRepository = chunkRepository;
    }

    StorageObject write(
            Bucket bucket,
            ObjectKey key,
            long sizeBytes,
            String contentType,
            ContentHash hash,
            Map<String, String> userMetadata,
            ObjectChunks chunks,
            Instant now) {
        Optional<StorageObject> current = objectRepository.findCurrentVersion(bucket.id(), key);
        if (current.isEmpty()) {
            return saveWithChunks(StorageObject.create(
                    bucket.id(), key, sizeBytes, contentType, hash, userMetadata, now), chunks);
        }

        StorageObject existing = current.get();
        if (!bucket.versioningEnabled()) {
            return saveWithChunks(existing.replaceContent(
                    sizeBytes, contentType, hash, userMetadata, now), chunks);
        }

        // The unique index permits only one current version per key, so the
        // predecessor must be demoted before the new version is inserted.
        objectRepository.demoteCurrentVersion(bucket.id(), key);
        return saveWithChunks(StorageObject.create(
                bucket.id(), key, sizeBytes, contentType, hash, userMetadata, now), chunks);
    }

    private StorageObject saveWithChunks(StorageObject object, ObjectChunks chunks) {
        StorageObject saved = objectRepository.save(object);
        chunkRepository.replaceChunks(saved.id(), chunks);
        return saved;
    }
}
