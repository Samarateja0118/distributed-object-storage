package com.dfs.metadata.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

interface ObjectChunkJpaRepository extends JpaRepository<ObjectChunkEntity, ObjectChunkEntity.Key> {

    List<ObjectChunkEntity> findByObjectIdOrderByChunkIndexAsc(UUID objectId);

    @Modifying(flushAutomatically = true)
    int deleteByObjectId(UUID objectId);
}
