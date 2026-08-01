package com.dfs.metadata.application.port.in;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.domain.model.ObjectChunk;
import com.dfs.metadata.domain.model.ObjectChunks;
import com.dfs.metadata.domain.model.StorageObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port for object metadata CRUD. Byte storage is out of scope here;
 * callers supply the already-computed size, content hash and chunk list.
 */
public interface ObjectMetadataUseCase {

    StorageObject putObject(PutObjectCommand command);

    StorageObject getObject(UUID tenantId, String bucketName, String objectKey);

    /**
     * The ordered chunk list for an object's current version, which is what a
     * download needs in order to fetch the content. Kept off {@link #getObject}
     * because it is the one part of an object that grows with the object.
     */
    ObjectChunks getObjectChunks(UUID tenantId, String bucketName, String objectKey);

    PageResult<StorageObject> listObjects(ListObjectsQuery query);

    List<StorageObject> listVersions(UUID tenantId, String bucketName, String objectKey);

    StorageObject updateUserMetadata(UpdateObjectMetadataCommand command);

    void deleteObject(UUID tenantId, String bucketName, String objectKey);

    /**
     * @param contentHash the composite digest of {@code chunks} (ADR 9), which
     *                    the service recomputes rather than trusts
     * @param chunks      the object's content, in order; empty only for a
     *                    zero-byte object
     */
    record PutObjectCommand(
            UUID tenantId,
            String bucketName,
            String objectKey,
            long sizeBytes,
            String contentType,
            String contentHash,
            List<ObjectChunk> chunks,
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
