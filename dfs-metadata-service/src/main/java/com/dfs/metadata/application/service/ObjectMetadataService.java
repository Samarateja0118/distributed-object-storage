package com.dfs.metadata.application.service;

import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.application.port.in.ObjectMetadataUseCase;
import com.dfs.metadata.application.port.out.BucketRepositoryPort;
import com.dfs.metadata.application.port.out.ObjectChunkRepositoryPort;
import com.dfs.metadata.application.port.out.ObjectMetadataRepositoryPort;
import com.dfs.metadata.domain.exception.BucketNotFoundException;
import com.dfs.metadata.domain.exception.ObjectNotFoundException;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;
import com.dfs.metadata.domain.model.ContentHash;
import com.dfs.metadata.domain.model.ObjectChunks;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ObjectMetadataService implements ObjectMetadataUseCase {

    private final ObjectMetadataRepositoryPort objectRepository;
    private final ObjectChunkRepositoryPort chunkRepository;
    private final BucketRepositoryPort bucketRepository;
    private final Clock clock;

    public ObjectMetadataService(
            ObjectMetadataRepositoryPort objectRepository,
            ObjectChunkRepositoryPort chunkRepository,
            BucketRepositoryPort bucketRepository,
            Clock clock) {
        this.objectRepository = objectRepository;
        this.chunkRepository = chunkRepository;
        this.bucketRepository = bucketRepository;
        this.clock = clock;
    }

    /**
     * Records an object and the chunk list making up its content.
     *
     * <p>The list is checked against the declared hash and size before anything
     * is written. Rejecting here costs one hash of a few hundred bytes; accepting
     * a list that does not describe the content would produce an object that
     * reads back as something other than what was uploaded, and nothing
     * downstream recomputes the digest to notice.
     */
    @Override
    public StorageObject putObject(PutObjectCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), command.bucketName());
        ObjectKey key = new ObjectKey(command.objectKey());
        ContentHash hash = new ContentHash(command.contentHash());
        ObjectChunks chunks = new ObjectChunks(command.chunks());
        chunks.requireConsistentWith(hash, command.sizeBytes());
        Instant now = Instant.now(clock);

        Optional<StorageObject> current = objectRepository.findCurrentVersion(bucket.id(), key);
        if (current.isEmpty()) {
            return saveWithChunks(StorageObject.create(
                    bucket.id(), key, command.sizeBytes(), command.contentType(), hash,
                    command.userMetadata(), now), chunks);
        }

        StorageObject existing = current.get();
        if (!bucket.versioningEnabled()) {
            return saveWithChunks(existing.replaceContent(
                    command.sizeBytes(), command.contentType(), hash, command.userMetadata(), now), chunks);
        }

        // The unique index permits only one current version per key, so the
        // predecessor must be demoted before the new version is inserted.
        objectRepository.demoteCurrentVersion(bucket.id(), key);
        return saveWithChunks(StorageObject.create(
                bucket.id(), key, command.sizeBytes(), command.contentType(), hash,
                command.userMetadata(), now), chunks);
    }

    /**
     * Both writes share the caller's transaction, so an object never becomes
     * visible without the chunk list saying where its content is.
     */
    private StorageObject saveWithChunks(StorageObject object, ObjectChunks chunks) {
        StorageObject saved = objectRepository.save(object);
        chunkRepository.replaceChunks(saved.id(), chunks);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjectChunks getObjectChunks(UUID tenantId, String bucketName, String objectKey) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        StorageObject object = requireCurrentVersion(bucket, new ObjectKey(objectKey));
        return chunkRepository.findChunks(object.id());
    }

    @Override
    @Transactional(readOnly = true)
    public StorageObject getObject(UUID tenantId, String bucketName, String objectKey) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        return requireCurrentVersion(bucket, new ObjectKey(objectKey));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StorageObject> listObjects(ListObjectsQuery query) {
        Bucket bucket = requireBucket(query.tenantId(), query.bucketName());
        return objectRepository.findCurrentVersionsByPrefix(bucket.id(), query.prefix(), query.pageQuery());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorageObject> listVersions(UUID tenantId, String bucketName, String objectKey) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        ObjectKey key = new ObjectKey(objectKey);
        List<StorageObject> versions = objectRepository.findAllVersions(bucket.id(), key);
        if (versions.isEmpty()) {
            throw new ObjectNotFoundException(bucket.name().value(), key.value());
        }
        return versions;
    }

    @Override
    public StorageObject updateUserMetadata(UpdateObjectMetadataCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), command.bucketName());
        StorageObject current = requireCurrentVersion(bucket, new ObjectKey(command.objectKey()));
        return objectRepository.save(current.withUserMetadata(command.userMetadata(), Instant.now(clock)));
    }

    @Override
    public void deleteObject(UUID tenantId, String bucketName, String objectKey) {
        Bucket bucket = requireBucket(tenantId, bucketName);
        ObjectKey key = new ObjectKey(objectKey);
        if (objectRepository.softDeleteAllVersions(bucket.id(), key) == 0) {
            throw new ObjectNotFoundException(bucket.name().value(), key.value());
        }
    }

    private Bucket requireBucket(UUID tenantId, String bucketName) {
        BucketName name = new BucketName(bucketName);
        return bucketRepository.findByTenantAndName(tenantId, name)
                .orElseThrow(() -> new BucketNotFoundException(name.value()));
    }

    private StorageObject requireCurrentVersion(Bucket bucket, ObjectKey key) {
        return objectRepository.findCurrentVersion(bucket.id(), key)
                .orElseThrow(() -> new ObjectNotFoundException(bucket.name().value(), key.value()));
    }
}
