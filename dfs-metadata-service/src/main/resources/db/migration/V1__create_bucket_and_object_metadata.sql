-- Phase 1: bucket and object metadata control plane.
-- Chunk, replica, and multipart tables arrive in Phase 2.

CREATE TABLE buckets (
    id                 UUID         PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    name               VARCHAR(63)  NOT NULL,
    versioning_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    default_encryption VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT ck_buckets_encryption CHECK (default_encryption IN ('NONE', 'AES256'))
);

-- A tenant may reuse a bucket name only after the previous bucket is purged.
CREATE UNIQUE INDEX ux_buckets_tenant_name_live
    ON buckets (tenant_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_buckets_tenant_live
    ON buckets (tenant_id, name)
    WHERE deleted_at IS NULL;

CREATE TABLE objects (
    id            UUID          PRIMARY KEY,
    bucket_id     UUID          NOT NULL REFERENCES buckets (id),
    object_key    VARCHAR(1024) NOT NULL,
    version_id    UUID          NOT NULL,
    is_latest     BOOLEAN       NOT NULL DEFAULT TRUE,
    size_bytes    BIGINT        NOT NULL,
    content_type  VARCHAR(255)  NOT NULL,
    content_hash  VARCHAR(64)   NOT NULL,
    etag          VARCHAR(64)   NOT NULL,
    user_metadata JSONB         NOT NULL DEFAULT '{}'::JSONB,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_objects_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT ux_objects_bucket_key_version UNIQUE (bucket_id, object_key, version_id)
);

-- Invariant: at most one live current version per key, for versioned and
-- non-versioned buckets alike.
CREATE UNIQUE INDEX ux_objects_current_version
    ON objects (bucket_id, object_key)
    WHERE is_latest AND deleted_at IS NULL;

-- Serves prefix listing and version history, both of which filter on live rows.
CREATE INDEX ix_objects_bucket_key_live
    ON objects (bucket_id, object_key)
    WHERE deleted_at IS NULL;
