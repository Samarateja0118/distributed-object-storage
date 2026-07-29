package com.dfs.metadata.application.port.out;

import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.PageResult;
import com.dfs.metadata.domain.model.ObjectKey;
import com.dfs.metadata.domain.model.StorageObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for object metadata persistence. All lookups exclude
 * soft-deleted rows.
 */
public interface ObjectMetadataRepositoryPort {

    StorageObject save(StorageObject storageObject);

    Optional<StorageObject> findCurrentVersion(UUID bucketId, ObjectKey key);

    List<StorageObject> findAllVersions(UUID bucketId, ObjectKey key);

    PageResult<StorageObject> findCurrentVersionsByPrefix(UUID bucketId, String prefix, PageQuery pageQuery);

    boolean existsInBucket(UUID bucketId);

    /**
     * Soft-deletes every live version of a key and returns how many rows changed.
     */
    int softDeleteAllVersions(UUID bucketId, ObjectKey key);

    /**
     * Clears the current-version flag on a key so a newer version can take it.
     */
    void demoteCurrentVersion(UUID bucketId, ObjectKey key);
}
