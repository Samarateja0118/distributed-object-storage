package com.dfs.metadata.api.rest;

import com.dfs.common.security.DfsTokenClaims;
import com.dfs.metadata.api.rest.dto.ChunkPayload;
import com.dfs.metadata.api.rest.dto.ObjectChunksResponse;
import com.dfs.metadata.api.rest.dto.ObjectResponse;
import com.dfs.metadata.api.rest.dto.PageResponse;
import com.dfs.metadata.api.rest.dto.PutObjectRequest;
import com.dfs.metadata.api.rest.dto.UpdateObjectMetadataRequest;
import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.in.ObjectMetadataUseCase;
import com.dfs.metadata.domain.model.StorageObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/buckets/{bucketName}")
public class ObjectMetadataController {

    private final ObjectMetadataUseCase objectUseCase;

    public ObjectMetadataController(ObjectMetadataUseCase objectUseCase) {
        this.objectUseCase = objectUseCase;
    }

    @PutMapping("/objects/{*objectKey}")
    public ResponseEntity<ObjectResponse> putObject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @Valid @RequestBody PutObjectRequest request) {
        StorageObject object = objectUseCase.putObject(new ObjectMetadataUseCase.PutObjectCommand(
                DfsTokenClaims.tenantId(jwt),
                bucketName,
                normalizeKey(objectKey),
                request.sizeBytes(),
                request.contentType(),
                request.contentHash(),
                request.chunks().stream().map(ChunkPayload::toDomain).toList(),
                request.userMetadata() == null ? Map.of() : request.userMetadata()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(ObjectResponse.from(object));
    }

    @GetMapping("/objects/{*objectKey}")
    public ResponseEntity<ObjectResponse> getObject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        StorageObject object = objectUseCase.getObject(DfsTokenClaims.tenantId(jwt), bucketName, normalizeKey(objectKey));
        return ResponseEntity.ok()
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(ObjectResponse.from(object));
    }

    /**
     * The chunk list for an object's current version, which is what a download
     * needs in order to fetch its content.
     *
     * <p>Under its own path for the same reason version history is: a
     * trailing-wildcard object key would otherwise swallow a {@code /chunks}
     * suffix. It is a separate call rather than a field on the object so that
     * listing a page of objects does not carry every chunk of every one.
     */
    @GetMapping("/object-chunks/{*objectKey}")
    public ObjectChunksResponse getObjectChunks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        return ObjectChunksResponse.from(
                objectUseCase.getObjectChunks(DfsTokenClaims.tenantId(jwt), bucketName, normalizeKey(objectKey)));
    }

    @GetMapping("/objects")
    public PageResponse<ObjectResponse> listObjects(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.from(
                objectUseCase.listObjects(new ObjectMetadataUseCase.ListObjectsQuery(
                        DfsTokenClaims.tenantId(jwt), bucketName, prefix, new PageQuery(page, size))),
                ObjectResponse::from);
    }

    /**
     * Version history lives under its own path because a trailing-wildcard object
     * key would otherwise swallow a {@code /versions} suffix.
     */
    @GetMapping("/object-versions/{*objectKey}")
    public List<ObjectResponse> listVersions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        return objectUseCase.listVersions(DfsTokenClaims.tenantId(jwt), bucketName, normalizeKey(objectKey)).stream()
                .map(ObjectResponse::from)
                .toList();
    }

    @PatchMapping("/objects/{*objectKey}")
    public ObjectResponse updateUserMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @Valid @RequestBody UpdateObjectMetadataRequest request) {
        StorageObject object = objectUseCase.updateUserMetadata(
                new ObjectMetadataUseCase.UpdateObjectMetadataCommand(
                        DfsTokenClaims.tenantId(jwt), bucketName, normalizeKey(objectKey), request.userMetadata()));
        return ObjectResponse.from(object);
    }

    @DeleteMapping("/objects/{*objectKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        objectUseCase.deleteObject(DfsTokenClaims.tenantId(jwt), bucketName, normalizeKey(objectKey));
    }

    /**
     * A trailing-wildcard path variable keeps the leading separator; object keys
     * are stored without it.
     */
    private static String normalizeKey(String rawKey) {
        return rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
    }
}
