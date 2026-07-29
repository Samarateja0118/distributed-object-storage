package com.dfs.auth.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    List<UserEntity> findByTenantIdAndDeletedAtIsNullOrderByEmailAsc(UUID tenantId);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
