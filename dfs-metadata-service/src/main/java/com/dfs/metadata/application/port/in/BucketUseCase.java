package com.dfs.metadata.application.port.in;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.domain.model.Bucket;
import com.dfs.metadata.domain.model.EncryptionType;

import java.util.UUID;

/**
 * Inbound port for bucket lifecycle operations.
 */
public interface BucketUseCase {

    Bucket createBucket(CreateBucketCommand command);

    Bucket getBucket(UUID tenantId, String bucketName);

    PageResult<Bucket> listBuckets(UUID tenantId, PageQuery pageQuery);

    Bucket updateBucket(UpdateBucketCommand command);

    void deleteBucket(UUID tenantId, String bucketName);

    record CreateBucketCommand(
            UUID tenantId,
            String name,
            boolean versioningEnabled,
            EncryptionType defaultEncryption) {
    }

    record UpdateBucketCommand(
            UUID tenantId,
            String name,
            boolean versioningEnabled) {
    }
}
