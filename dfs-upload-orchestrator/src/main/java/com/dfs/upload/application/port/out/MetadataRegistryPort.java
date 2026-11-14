package com.dfs.upload.application.port.out;

import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.RegisteredObject;
import com.dfs.upload.domain.model.UploadedPart;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * Stages one part's chunk list in an open multipart upload (ADR 12). The
     * metadata service owns the session, checks the list against the part's
     * hash and size, and scopes the upload id to the caller's tenant.
     *
     * @throws com.dfs.upload.domain.exception.UploadNotFoundException if no such
     *         upload is open for the caller
     * @throws com.dfs.upload.domain.exception.InvalidPartException if the part is
     *         refused as the caller's error
     */
    UploadedPart registerPart(RegisterPartCommand command, CallerToken token);

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

    /**
     * @param contentHash the composite digest of the part's chunks, which becomes
     *                    the part's etag
     */
    record RegisterPartCommand(
            String bucketName,
            UUID uploadId,
            int partNumber,
            long sizeBytes,
            String contentHash,
            List<ObjectChunkRef> chunks) {
    }
}
