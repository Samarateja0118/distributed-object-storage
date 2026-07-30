package com.dfs.upload.infrastructure.adapter.out.blob;

import com.dfs.upload.application.port.out.BlobStorePort;
import com.dfs.upload.domain.exception.ObjectNotFoundException;
import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.infrastructure.adapter.out.DownstreamErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the blob node over REST.
 *
 * <p>REST rather than gRPC on purpose: Phase 1 is a single node and the gRPC
 * surface is not implemented until Phase 2, when chunked transfer needs it.
 */
@Component
public class RestBlobStoreAdapter implements BlobStorePort {

    static final String SERVICE_NAME = "blob node";

    private final RestClient blobClient;

    public RestBlobStoreAdapter(RestClient blobClient) {
        this.blobClient = blobClient;
    }

    @Override
    public void store(String digest, byte[] content, CallerToken token) {
        blobClient.put()
                .uri("/internal/v1/blobs/{digest}", digest)
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw DownstreamErrors.from(SERVICE_NAME, response);
                })
                .toBodilessEntity();
    }

    @Override
    public byte[] fetch(String digest, CallerToken token) {
        return blobClient.get()
                .uri("/internal/v1/blobs/{digest}", digest)
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // Metadata pointed at this digest, so a miss here is not a bad
                    // request — it means the two halves have diverged and the object
                    // is unreadable. Reported as such rather than as a plain 404.
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new ObjectNotFoundException(
                                "Metadata references blob %s but the blob node does not have it".formatted(digest));
                    }
                    throw DownstreamErrors.from(SERVICE_NAME, response);
                })
                .body(byte[].class);
    }
}
