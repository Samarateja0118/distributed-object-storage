package com.dfs.metadata.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectJpaRepository extends JpaRepository<ObjectEntity, UUID> {

    Optional<ObjectEntity> findByBucketIdAndObjectKeyAndLatestTrueAndDeletedAtIsNull(UUID bucketId, String objectKey);

    List<ObjectEntity> findByBucketIdAndObjectKeyAndDeletedAtIsNullOrderByCreatedAtDesc(UUID bucketId, String objectKey);

    Page<ObjectEntity> findByBucketIdAndObjectKeyStartingWithAndLatestTrueAndDeletedAtIsNull(
            UUID bucketId, String prefix, Pageable pageable);

    boolean existsByBucketIdAndDeletedAtIsNull(UUID bucketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ObjectEntity o
               SET o.deletedAt = :now, o.latest = false, o.updatedAt = :now
             WHERE o.bucketId = :bucketId
               AND o.objectKey = :objectKey
               AND o.deletedAt IS NULL
            """)
    int softDeleteAllVersions(
            @Param("bucketId") UUID bucketId,
            @Param("objectKey") String objectKey,
            @Param("now") Instant now);

    /**
     * Issued as immediate DML: Hibernate's action queue flushes inserts before
     * updates, so demoting the previous version via a dirty entity would race the
     * new version's insert against the single-current-version unique index.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ObjectEntity o
               SET o.latest = false, o.updatedAt = :now
             WHERE o.bucketId = :bucketId
               AND o.objectKey = :objectKey
               AND o.latest = true
               AND o.deletedAt IS NULL
            """)
    int demoteCurrentVersion(
            @Param("bucketId") UUID bucketId,
            @Param("objectKey") String objectKey,
            @Param("now") Instant now);
}
