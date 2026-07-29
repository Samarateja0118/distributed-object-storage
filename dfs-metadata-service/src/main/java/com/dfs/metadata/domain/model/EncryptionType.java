package com.dfs.metadata.domain.model;

/**
 * Server-side encryption applied to objects in a bucket.
 * Enforcement lands in Phase 3; Phase 1 records the intent only.
 */
public enum EncryptionType {
    NONE,
    AES256
}
