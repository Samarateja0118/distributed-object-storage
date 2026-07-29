-- Phase 1: identity control plane. Roles are stored per user but only coarse
-- membership is enforced; per-bucket authorization arrives in Phase 3.

CREATE TABLE tenants (
    id         UUID        PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    slug       VARCHAR(63)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    deleted_at TIMESTAMPTZ
);

-- Slugs are reusable only once the previous tenant is purged, mirroring how
-- bucket names behave in the metadata service.
CREATE UNIQUE INDEX ux_tenants_slug_live
    ON tenants (slug)
    WHERE deleted_at IS NULL;

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_users_role CHECK (role IN ('OWNER', 'MEMBER'))
);

-- Login takes an email and no tenant hint, so the address must identify exactly
-- one live account across the whole installation.
CREATE UNIQUE INDEX ux_users_email_live
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_users_tenant_live
    ON users (tenant_id)
    WHERE deleted_at IS NULL;

CREATE TABLE api_keys (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    user_id      UUID         NOT NULL REFERENCES users (id),
    name         VARCHAR(128) NOT NULL,
    key_prefix   VARCHAR(16)  NOT NULL,
    secret_hash  VARCHAR(72)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

-- The prefix is the lookup key on the verification path, so it must be unique
-- across all keys ever issued, including revoked ones.
CREATE UNIQUE INDEX ux_api_keys_prefix
    ON api_keys (key_prefix);

CREATE INDEX ix_api_keys_tenant_active
    ON api_keys (tenant_id)
    WHERE revoked_at IS NULL;
