package com.dfs.auth.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);

    Optional<ApiKeyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Written as direct DML so stamping usage never loads the row into the
     * persistence context, where it would compete with the verification path.
     */
    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.lastUsedAt = :usedAt WHERE k.id = :id")
    void updateLastUsedAt(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
