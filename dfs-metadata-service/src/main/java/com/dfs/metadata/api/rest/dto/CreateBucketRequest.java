package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.EncryptionType;

/**
 * Bucket name is taken from the request path; the body carries settings only.
 */
public record CreateBucketRequest(
        boolean versioningEnabled,
        EncryptionType defaultEncryption) {
}
