package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.RegisteredObject;

import java.util.Map;

/**
 * The pointer half of an object: which key, in which bucket, owned by which
 * tenant, referring to which digest.
 */
public interface MetadataRegistryPort {

    RegisteredObject register(RegisterObjectCommand command, CallerToken token);

    RegisteredObject lookup(String bucketName, String objectKey, CallerToken token);

    record RegisterObjectCommand(
            String bucketName,
            String objectKey,
            long sizeBytes,
            String contentType,
            String contentHash,
            Map<String, String> userMetadata) {
    }
}
