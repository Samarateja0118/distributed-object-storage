package com.dfs.metadata.application.service;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.application.port.in.BucketUseCase;
import com.dfs.metadata.application.port.out.BucketRepositoryPort;
import com.dfs.metadata.application.port.out.ObjectMetadataRepositoryPort;
import com.dfs.metadata.domain.exception.BucketAlreadyExistsException;
import com.dfs.metadata.domain.exception.BucketNotEmptyException;
import com.dfs.metadata.domain.exception.BucketNotFoundException;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.BucketName;
import com.dfs.metadata.domain.model.EncryptionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class BucketService implements BucketUseCase {

    private final BucketRepositoryPort bucketRepository;
    private final ObjectMetadataRepositoryPort objectRepository;
    private final Clock clock;

    public BucketService(
            BucketRepositoryPort bucketRepository,
            ObjectMetadataRepositoryPort objectRepository,
            Clock clock) {
        this.bucketRepository = bucketRepository;
        this.objectRepository = objectRepository;
        this.clock = clock;
    }

    @Override
    public Bucket createBucket(CreateBucketCommand command) {
        BucketName name = new BucketName(command.name());
        if (bucketRepository.existsByTenantAndName(command.tenantId(), name)) {
            throw new BucketAlreadyExistsException(name.value());
        }
        EncryptionType encryption =
                command.defaultEncryption() == null ? EncryptionType.NONE : command.defaultEncryption();
        Bucket bucket = Bucket.create(
                command.tenantId(), name, command.versioningEnabled(), encryption, Instant.now(clock));
        return bucketRepository.save(bucket);
    }

    @Override
    @Transactional(readOnly = true)
    public Bucket getBucket(UUID tenantId, String bucketName) {
        return requireBucket(tenantId, new BucketName(bucketName));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Bucket> listBuckets(UUID tenantId, PageQuery pageQuery) {
        return bucketRepository.findAllByTenant(tenantId, pageQuery);
    }

    @Override
    public Bucket updateBucket(UpdateBucketCommand command) {
        Bucket bucket = requireBucket(command.tenantId(), new BucketName(command.name()));
        return bucketRepository.save(bucket.withVersioning(command.versioningEnabled(), Instant.now(clock)));
    }

    @Override
    public void deleteBucket(UUID tenantId, String bucketName) {
        Bucket bucket = requireBucket(tenantId, new BucketName(bucketName));
        if (objectRepository.existsInBucket(bucket.id())) {
            throw new BucketNotEmptyException(bucket.name().value());
        }
        bucketRepository.save(bucket.markDeleted(Instant.now(clock)));
    }

    private Bucket requireBucket(UUID tenantId, BucketName name) {
        return bucketRepository.findByTenantAndName(tenantId, name)
                .orElseThrow(() -> new BucketNotFoundException(name.value()));
    }
}
