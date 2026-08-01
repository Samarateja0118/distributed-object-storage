package com.dfs.upload.application.port.in;

import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;

import java.io.InputStream;
import java.util.Map;

/**
 * The join between bytes and metadata. Until this existed an object could be
 * registered with no content behind it; going through here is what keeps the
 * two halves in step.
 */
public interface ObjectTransferUseCase {

    RegisteredObject upload(UploadCommand command);

    ObjectDownload download(DownloadQuery query);

    /**
     * @param content the object's bytes, consumed as they arrive. A stream
     *                rather than an array because the point of chunking is that
     *                the whole object never has to be in memory at once (ADR 8);
     *                its length is therefore not known until it has been read.
     */
    record UploadCommand(
            String bucketName,
            String objectKey,
            InputStream content,
            String contentType,
            Map<String, String> userMetadata,
            CallerToken token) {
    }

    record DownloadQuery(String bucketName, String objectKey, CallerToken token) {
    }
}
