package com.dfs.auth.infrastructure.adapter.out.persistence;

import com.dfs.auth.application.port.out.UserRepositoryPort;
import com.dfs.auth.domain.model.EmailAddress;
import com.dfs.auth.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = repository.findById(user.id())
                .orElseGet(() -> AuthPersistenceMapper.toEntity(user));
        entity.apply(user.passwordHash(), user.role(), user.updatedAt(), user.deletedAt());
        return AuthPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return repository.findByIdAndDeletedAtIsNull(userId).map(AuthPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return repository.findByEmailAndDeletedAtIsNull(email.value()).map(AuthPersistenceMapper::toDomain);
    }

    @Override
    public List<User> findAllByTenant(UUID tenantId) {
        return repository.findByTenantIdAndDeletedAtIsNullOrderByEmailAsc(tenantId).stream()
                .map(AuthPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return repository.existsByEmailAndDeletedAtIsNull(email.value());
    }
}
