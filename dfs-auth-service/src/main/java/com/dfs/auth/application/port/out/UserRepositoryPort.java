package com.dfs.auth.application.port.out;

import com.dfs.auth.domain.model.EmailAddress;
import com.dfs.auth.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for user persistence. Lookups exclude soft-deleted rows.
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID userId);

    Optional<User> findByEmail(EmailAddress email);

    List<User> findAllByTenant(UUID tenantId);

    boolean existsByEmail(EmailAddress email);
}
