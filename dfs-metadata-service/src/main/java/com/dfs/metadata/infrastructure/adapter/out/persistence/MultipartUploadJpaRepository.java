package com.dfs.metadata.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface MultipartUploadJpaRepository extends JpaRepository<MultipartUploadEntity, UUID> {

    Optional<MultipartUploadEntity> findByIdAndBucketId(UUID id, UUID bucketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MultipartUploadEntity u where u.id = :id and u.bucketId = :bucketId")
    Optional<MultipartUploadEntity> findByIdAndBucketIdForUpdate(@Param("id") UUID id, @Param("bucketId") UUID bucketId);

    /**
     * A bulk delete rather than {@code deleteById}: the staged chunks go by the
     * database's ON DELETE CASCADE, which a JPA remove of the parent would not
     * know about, and there is no reason to load the session to delete it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MultipartUploadEntity u where u.id = :id")
    int deleteSession(@Param("id") UUID id);

    /**
     * Native because JPQL has neither LIMIT in a subquery nor SKIP LOCKED.
     * SKIP LOCKED is what lets two instances sweep at once: each takes the
     * expired rows the other has not locked, so neither blocks nor deletes a
     * session the other is mid-way through — including one being completed,
     * whose row completion holds for update.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM multipart_uploads
            WHERE id IN (
                SELECT id FROM multipart_uploads
                WHERE expires_at <= :now
                ORDER BY expires_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now, @Param("limit") int limit);
}
