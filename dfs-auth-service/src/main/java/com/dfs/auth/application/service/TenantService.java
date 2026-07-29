package com.dfs.auth.application.service;

import com.dfs.auth.application.port.in.TenantUseCase;
import com.dfs.auth.application.port.out.PasswordHasherPort;
import com.dfs.auth.application.port.out.TenantRepositoryPort;
import com.dfs.auth.application.port.out.UserRepositoryPort;
import com.dfs.auth.domain.exception.TenantAlreadyExistsException;
import com.dfs.auth.domain.exception.TenantNotFoundException;
import com.dfs.auth.domain.exception.UserAlreadyExistsException;
import com.dfs.auth.domain.model.EmailAddress;
import com.dfs.auth.domain.model.Tenant;
import com.dfs.auth.domain.model.TenantSlug;
import com.dfs.auth.domain.model.User;
import com.dfs.auth.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TenantService implements TenantUseCase {

    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public TenantService(
            TenantRepositoryPort tenantRepository,
            UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public TenantRegistration registerTenant(RegisterTenantCommand command) {
        TenantSlug slug = new TenantSlug(command.slug());
        EmailAddress ownerEmail = new EmailAddress(command.ownerEmail());
        if (tenantRepository.existsBySlug(slug)) {
            throw new TenantAlreadyExistsException(slug.value());
        }
        if (userRepository.existsByEmail(ownerEmail)) {
            throw new UserAlreadyExistsException(ownerEmail.value());
        }

        Instant now = Instant.now(clock);
        Tenant tenant = tenantRepository.save(Tenant.create(command.name(), slug, now));
        User owner = userRepository.save(User.create(
                tenant.id(), ownerEmail, passwordHasher.hash(command.ownerPassword()), UserRole.OWNER, now));
        return new TenantRegistration(tenant, owner);
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Override
    public User addUser(AddUserCommand command) {
        Tenant tenant = getTenant(command.tenantId());
        EmailAddress email = new EmailAddress(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email.value());
        }
        return userRepository.save(User.create(
                tenant.id(), email, passwordHasher.hash(command.password()), UserRole.MEMBER, Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> listUsers(UUID tenantId) {
        return userRepository.findAllByTenant(tenantId);
    }
}
