package com.dfs.metadata.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MultipartUploadChunkJpaRepository
        extends JpaRepository<MultipartUploadChunkEntity, MultipartUploadChunkEntity.Key> {

    List<MultipartUploadChunkEntity> findByUploadIdOrderByPartNumberAscChunkIndexAsc(UUID uploadId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MultipartUploadChunkEntity c where c.uploadId = :uploadId and c.partNumber = :partNumber")
    int deletePart(@Param("uploadId") UUID uploadId, @Param("partNumber") int partNumber);
}
