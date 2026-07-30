package com.dfs.upload.application.port.in;

import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;

import java.util.Map;

/**
 * The join between bytes and metadata. Until this existed an object could be
 * registered with no content behind it; going through here is what keeps the
 * two halves in step.
 */
public interface ObjectTransferUseCase {

    RegisteredObject upload(UploadCommand command);

    ObjectDownload download(DownloadQuery query);

    record UploadCommand(
            String bucketName,
            String objectKey,
            byte[] content,
            String contentType,
            Map<String, String> userMetadata,
            CallerToken token) {
    }

    record DownloadQuery(String bucketName, String objectKey, CallerToken token) {
    }
}
