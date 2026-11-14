package com.dfs.upload.api.rest;

import com.dfs.upload.api.rest.dto.UploadedObjectResponse;
import com.dfs.upload.api.rest.dto.UploadedPartResponse;
import com.dfs.upload.application.port.in.ObjectTransferUseCase;
import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;
import com.dfs.upload.domain.model.UploadedPart;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * The data plane: bytes in, bytes out.
 *
 * <p>Mirrors the metadata service's object paths so a caller uses the same
 * bucket and key here as there, with the body being the content rather than a
 * metadata document.
 */
@RestController
@RequestMapping("/api/v1/buckets/{bucketName}")
public class ObjectContentController {

    /**
     * Prefix for caller-supplied metadata. Headers rather than a JSON envelope
     * because the body is the object's bytes and cannot also carry fields.
     */
    private static final String USER_METADATA_PREFIX = "x-dfs-meta-";

    private final ObjectTransferUseCase objects;

    public ObjectContentController(ObjectTransferUseCase objects) {
        this.objects = objects;
    }

    @PutMapping("/objects/{*objectKey}")
    public ResponseEntity<UploadedObjectResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            HttpServletRequest request,
            InputStream body) {

        // Handed over unread: the service chunks it as it arrives, so nothing
        // here needs the whole object, and the size limit is enforced against the
        // bytes actually read rather than a Content-Length a client can misstate.
        RegisteredObject registered = objects.upload(new ObjectTransferUseCase.UploadCommand(
                bucketName,
                normalizeKey(objectKey),
                body,
                request.getContentType(),
                userMetadataFrom(request),
                callerToken(jwt)));

        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + registered.etag() + "\"")
                .header("X-Object-Version-Id", registered.versionId().toString())
                .body(UploadedObjectResponse.from(registered));
    }

    /**
     * One part of a multipart upload: the bytes in, the part's etag out (ADR 12).
     *
     * <p>The rest of the session — opening it, listing parts, completing,
     * aborting — is the metadata service's, because none of it carries bytes.
     * This is the only multipart call that does, so it is the only one here.
     * The part number is checked by the metadata service, which owns the rule;
     * a number outside the range is refused as a 400 after the bytes are stored.
     */
    @PutMapping("/multipart-uploads/{uploadId}/parts/{partNumber}")
    public ResponseEntity<UploadedPartResponse> uploadPart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            InputStream body) {

        UploadedPart part = objects.uploadPart(new ObjectTransferUseCase.UploadPartCommand(
                bucketName, uploadId, partNumber, body, callerToken(jwt)));

        return ResponseEntity.ok()
                .eTag("\"" + part.etag() + "\"")
                .body(UploadedPartResponse.from(part));
    }

    /**
     * Also serves HEAD, which Spring answers with these headers and no body.
     *
     * <p>Returns a {@link Resource} over the chunk stream rather than a byte
     * array, so chunks are fetched as the response is written and a download
     * never assembles the whole object in memory.
     */
    @GetMapping("/objects/{*objectKey}")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {

        ObjectDownload download = objects.download(new ObjectTransferUseCase.DownloadQuery(
                bucketName, normalizeKey(objectKey), callerToken(jwt)));

        RegisteredObject object = download.object();
        HttpHeaders headers = new HttpHeaders();
        object.userMetadata().forEach((key, value) -> headers.add(USER_METADATA_PREFIX + key, value));

        return ResponseEntity.ok()
                .headers(headers)
                // Uploads through this service always set a content type, but the
                // metadata API accepts objects without one, so a record registered
                // directly can still arrive here with it null.
                .contentType(object.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(object.contentType()))
                .contentLength(object.sizeBytes())
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(new InputStreamResource(download.content()));
    }

    /**
     * Replays the caller's own token downstream. Taken from the validated
     * {@link Jwt} rather than re-read from the Authorization header, so what is
     * forwarded is exactly what this service verified.
     */
    private static CallerToken callerToken(Jwt jwt) {
        return new CallerToken(jwt.getTokenValue());
    }

    private static Map<String, String> userMetadataFrom(HttpServletRequest request) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String header : Collections.list(request.getHeaderNames())) {
            String lowerCased = header.toLowerCase();
            if (lowerCased.startsWith(USER_METADATA_PREFIX) && lowerCased.length() > USER_METADATA_PREFIX.length()) {
                metadata.put(lowerCased.substring(USER_METADATA_PREFIX.length()), request.getHeader(header));
            }
        }
        return metadata;
    }

    /**
     * A trailing-wildcard path variable keeps the leading separator; object keys
     * are stored without it.
     */
    private static String normalizeKey(String rawKey) {
        return rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
    }
}
