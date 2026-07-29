package com.dfs.auth.infrastructure.adapter.out.persistence;

import com.dfs.auth.domain.model.ApiKey;
import com.dfs.auth.domain.model.EmailAddress;
import com.dfs.auth.domain.model.Tenant;
import com.dfs.auth.domain.model.TenantSlug;
import com.dfs.auth.domain.model.User;

final class AuthPersistenceMapper {

    private AuthPersistenceMapper() {
    }

    static Tenant toDomain(TenantEntity entity) {
        return new Tenant(
                entity.getId(),
                entity.getName(),
                new TenantSlug(entity.getSlug()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static TenantEntity toEntity(Tenant tenant) {
        return new TenantEntity(
                tenant.id(),
                tenant.name(),
                tenant.slug().value(),
                tenant.createdAt(),
                tenant.updatedAt(),
                tenant.deletedAt());
    }

    static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getTenantId(),
                new EmailAddress(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static UserEntity toEntity(User user) {
        return new UserEntity(
                user.id(),
                user.tenantId(),
                user.email().value(),
                user.passwordHash(),
                user.role(),
                user.createdAt(),
                user.updatedAt(),
                user.deletedAt());
    }

    static ApiKey toDomain(ApiKeyEntity entity) {
        return new ApiKey(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getSecretHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getLastUsedAt(),
                entity.getRevokedAt());
    }

    static ApiKeyEntity toEntity(ApiKey apiKey) {
        return new ApiKeyEntity(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.userId(),
                apiKey.name(),
                apiKey.keyPrefix(),
                apiKey.secretHash(),
                apiKey.createdAt(),
                apiKey.expiresAt(),
                apiKey.lastUsedAt(),
                apiKey.revokedAt());
    }
}
