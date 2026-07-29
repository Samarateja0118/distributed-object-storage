package com.dfs.auth.domain.model;

/**
 * Phase 1 distinguishes only the tenant's founding account from everyone else.
 * Fine-grained permissions arrive with RBAC in Phase 3.
 */
public enum UserRole {
    OWNER,
    MEMBER
}
