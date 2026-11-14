package com.dfs.upload.infrastructure.adapter.out.metadata;

import com.dfs.upload.application.port.out.MetadataRegistryPort;
import com.dfs.upload.domain.exception.DownstreamServiceException;
import com.dfs.upload.domain.exception.InvalidPartException;
import com.dfs.upload.domain.exception.ObjectNotFoundException;
import com.dfs.upload.domain.exception.UploadNotFoundException;
import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.RegisteredObject;
import com.dfs.upload.domain.model.UploadedPart;
import com.dfs.upload.infrastructure.adapter.out.DownstreamErrors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Talks to the metadata service over its public REST API, as any other client
 * would, carrying the caller's token so the tenant claim still decides what is
 * visible.
 */
@Component
public class RestMetadataRegistryAdapter implements MetadataRegistryPort {

    static final String SERVICE_NAME = "metadata service";

    private static final String OBJECT_PATH = "/api/v1/buckets/{bucketName}/objects/{objectKey}";

    /**
     * Its own path rather than a suffix on the object one, because the object
     * key is a trailing wildcard there and would swallow a {@code /chunks}.
     */
    private static final String CHUNKS_PATH = "/api/v1/buckets/{bucketName}/object-chunks/{objectKey}";

    /**
     * A session's parts live under the session rather than under the object
     * key, whose trailing wildcard would otherwise swallow them (ADR 12).
     */
    private static final String PART_PATH =
            "/api/v1/buckets/{bucketName}/multipart-uploads/{uploadId}/parts/{partNumber}";

    /** Only for reading the detail out of a refusal; never for a success body. */
    private static final ObjectMapper PROBLEM_READER = new ObjectMapper();

    private final RestClient metadataClient;

    public RestMetadataRegistryAdapter(RestClient metadataClient) {
        this.metadataClient = metadataClient;
    }

    @Override
    public RegisteredObject register(RegisterObjectCommand command, CallerToken token) {
        MetadataObjectPayloads.ObjectResponse response = metadataClient.put()
                .uri(OBJECT_PATH, command.bucketName(), command.objectKey())
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MetadataObjectPayloads.PutObjectRequest(
                        command.sizeBytes(),
                        command.contentType(),
                        command.contentHash(),
                        command.chunks().stream().map(MetadataObjectPayloads.ChunkPayload::from).toList(),
                        command.userMetadata()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    // A missing bucket is the caller's mistake, not a gateway failure.
                    if (httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new ObjectNotFoundException(
                                "Bucket '%s' does not exist".formatted(command.bucketName()));
                    }
                    throw DownstreamErrors.from(SERVICE_NAME, httpResponse);
                })
                .body(MetadataObjectPayloads.ObjectResponse.class);

        return requireBody(response).toDomain();
    }

    @Override
    public RegisteredObject lookup(String bucketName, String objectKey, CallerToken token) {
        MetadataObjectPayloads.ObjectResponse response = metadataClient.get()
                .uri(OBJECT_PATH, bucketName, objectKey)
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    if (httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new ObjectNotFoundException(bucketName, objectKey);
                    }
                    throw DownstreamErrors.from(SERVICE_NAME, httpResponse);
                })
                .body(MetadataObjectPayloads.ObjectResponse.class);

        return requireBody(response).toDomain();
    }

    /**
     * The chunk list lives under its own path on the metadata service, so this is
     * a second call rather than a field on the object — which is what keeps a
     * listing of objects from carrying every chunk of every one.
     */
    @Override
    public List<ObjectChunkRef> fetchChunks(String bucketName, String objectKey, CallerToken token) {
        MetadataObjectPayloads.ObjectChunksResponse response = metadataClient.get()
                .uri(CHUNKS_PATH, bucketName, objectKey)
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    if (httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new ObjectNotFoundException(bucketName, objectKey);
                    }
                    throw DownstreamErrors.from(SERVICE_NAME, httpResponse);
                })
                .body(MetadataObjectPayloads.ObjectChunksResponse.class);

        if (response == null || response.chunks() == null) {
            throw new DownstreamServiceException(SERVICE_NAME, HttpStatus.OK.value(),
                    "success status carried no chunk list");
        }
        return response.chunks().stream().map(MetadataObjectPayloads.ChunkPayload::toDomain).toList();
    }

    /**
     * A 404 means the session is not open for this caller, and a 400 means the
     * part itself was refused; both are the caller's to act on, so neither is
     * reported as the 502 every other failure here becomes.
     */
    @Override
    public UploadedPart registerPart(RegisterPartCommand command, CallerToken token) {
        MetadataObjectPayloads.PartResponse response = metadataClient.put()
                .uri(PART_PATH, command.bucketName(), command.uploadId(), command.partNumber())
                .header(HttpHeaders.AUTHORIZATION, token.asBearerHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MetadataObjectPayloads.PutPartRequest(
                        command.sizeBytes(),
                        command.contentHash(),
                        command.chunks().stream().map(MetadataObjectPayloads.ChunkPayload::from).toList()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    if (httpResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new UploadNotFoundException(command.bucketName(), command.uploadId().toString());
                    }
                    if (httpResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        throw new InvalidPartException(problemDetail(httpResponse.getBody()));
                    }
                    throw DownstreamErrors.from(SERVICE_NAME, httpResponse);
                })
                .body(MetadataObjectPayloads.PartResponse.class);

        if (response == null) {
            throw new DownstreamServiceException(SERVICE_NAME, HttpStatus.OK.value(),
                    "success status carried no part body");
        }
        return response.toDomain();
    }

    /**
     * The {@code detail} of an RFC 7807 body, which is the part of the metadata
     * service's refusal a caller can act on, or a generic line if there is none.
     */
    private static String problemDetail(InputStream body) {
        try (body) {
            JsonNode detail = PROBLEM_READER.readTree(body).get("detail");
            if (detail != null && detail.isTextual() && !detail.asText().isBlank()) {
                return detail.asText();
            }
        } catch (IOException | RuntimeException unreadable) {
            // Fall through: the status alone still says what kind of failure this is.
        }
        return "The metadata service refused the part";
    }

    private static MetadataObjectPayloads.ObjectResponse requireBody(
            MetadataObjectPayloads.ObjectResponse response) {
        if (response == null) {
            throw new DownstreamServiceException(SERVICE_NAME, HttpStatus.OK.value(),
                    "success status carried no object body");
        }
        return response;
    }
}
