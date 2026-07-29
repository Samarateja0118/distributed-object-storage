package com.dfs.auth.application.port.in;

import com.dfs.auth.domain.model.Tenant;
import com.dfs.auth.domain.model.User;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for tenant onboarding and membership.
 */
public interface TenantUseCase {

    /**
     * Creates the tenant and its first user in one transaction: a tenant with no
     * way to authenticate into it would be unusable.
     */
    TenantRegistration registerTenant(RegisterTenantCommand command);

    Tenant getTenant(UUID tenantId);

    User addUser(AddUserCommand command);

    List<User> listUsers(UUID tenantId);

    record RegisterTenantCommand(
            String name,
            String slug,
            String ownerEmail,
            String ownerPassword) {
    }

    record AddUserCommand(
            UUID tenantId,
            String email,
            String password) {
    }

    record TenantRegistration(Tenant tenant, User owner) {
    }
}
