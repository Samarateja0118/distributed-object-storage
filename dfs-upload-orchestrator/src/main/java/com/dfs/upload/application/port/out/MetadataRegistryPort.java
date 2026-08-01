package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.RegisteredObject;

import java.util.List;
import java.util.Map;

/**
 * The pointer half of an object: which key, in which bucket, owned by which
 * tenant, made of which chunks.
 */
public interface MetadataRegistryPort {

    RegisteredObject register(RegisterObjectCommand command, CallerToken token);

    RegisteredObject lookup(String bucketName, String objectKey, CallerToken token);

    /**
     * The object's chunks in order, which is what a download needs in order to
     * ask for content. A separate call from {@link #lookup} because the metadata
     * service keeps it a separate resource — the chunk list is the part of an
     * object that grows with the object.
     */
    List<ObjectChunkRef> fetchChunks(String bucketName, String objectKey, CallerToken token);

    /**
     * @param contentHash the composite digest of {@code chunks} (ADR 9), not the
     *                    hash of the object's bytes
     */
    record RegisterObjectCommand(
            String bucketName,
            String objectKey,
            long sizeBytes,
            String contentType,
            String contentHash,
            List<ObjectChunkRef> chunks,
            Map<String, String> userMetadata) {
    }
}
