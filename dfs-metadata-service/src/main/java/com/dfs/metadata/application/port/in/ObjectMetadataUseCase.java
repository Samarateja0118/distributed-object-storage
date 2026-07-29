package com.dfs.metadata.application.port.in;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.domain.model.StorageObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port for object metadata CRUD. Byte storage is out of scope for Phase 1;
 * callers supply the already-computed size and content hash.
 */
public interface ObjectMetadataUseCase {

    StorageObject putObject(PutObjectCommand command);

    StorageObject getObject(UUID tenantId, String bucketName, String objectKey);

    PageResult<StorageObject> listObjects(ListObjectsQuery query);

    List<StorageObject> listVersions(UUID tenantId, String bucketName, String objectKey);

    StorageObject updateUserMetadata(UpdateObjectMetadataCommand command);

    void deleteObject(UUID tenantId, String bucketName, String objectKey);

    record PutObjectCommand(
            UUID tenantId,
            String bucketName,
            String objectKey,
            long sizeBytes,
            String contentType,
            String contentHash,
            Map<String, String> userMetadata) {
    }

    record UpdateObjectMetadataCommand(
            UUID tenantId,
            String bucketName,
            String objectKey,
            Map<String, String> userMetadata) {
    }

    record ListObjectsQuery(
            UUID tenantId,
            String bucketName,
            String prefix,
            PageQuery pageQuery) {
    }
}
