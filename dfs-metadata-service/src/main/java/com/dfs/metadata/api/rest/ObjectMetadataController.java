package com.dfs.metadata.api.rest;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buckets/{bucketName}")
public class ObjectMetadataController {

    private final ObjectMetadataUseCase objectUseCase;

    public ObjectMetadataController(ObjectMetadataUseCase objectUseCase) {
        this.objectUseCase = objectUseCase;
    }

    @PutMapping("/objects/{*objectKey}")
    public ResponseEntity<ObjectResponse> putObject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @Valid @RequestBody PutObjectRequest request) {
        StorageObject object = objectUseCase.putObject(new ObjectMetadataUseCase.PutObjectCommand(
                tenantId,
                bucketName,
                normalizeKey(objectKey),
                request.sizeBytes(),
                request.contentType(),
                request.contentHash(),
                request.userMetadata() == null ? Map.of() : request.userMetadata()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(ObjectResponse.from(object));
    }

    @GetMapping("/objects/{*objectKey}")
    public ResponseEntity<ObjectResponse> getObject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        StorageObject object = objectUseCase.getObject(tenantId, bucketName, normalizeKey(objectKey));
        return ResponseEntity.ok()
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(ObjectResponse.from(object));
    }

    @GetMapping("/objects")
    public PageResponse<ObjectResponse> listObjects(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.from(
                objectUseCase.listObjects(new ObjectMetadataUseCase.ListObjectsQuery(
                        tenantId, bucketName, prefix, new PageQuery(page, size))),
                ObjectResponse::from);
    }

    /**
     * Version history lives under its own path because a trailing-wildcard object
     * key would otherwise swallow a {@code /versions} suffix.
     */
    @GetMapping("/object-versions/{*objectKey}")
    public List<ObjectResponse> listVersions(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        return objectUseCase.listVersions(tenantId, bucketName, normalizeKey(objectKey)).stream()
                .map(ObjectResponse::from)
                .toList();
    }

    @PatchMapping("/objects/{*objectKey}")
    public ObjectResponse updateUserMetadata(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @Valid @RequestBody UpdateObjectMetadataRequest request) {
        StorageObject object = objectUseCase.updateUserMetadata(
                new ObjectMetadataUseCase.UpdateObjectMetadataCommand(
                        tenantId, bucketName, normalizeKey(objectKey), request.userMetadata()));
        return ObjectResponse.from(object);
    }

    @DeleteMapping("/objects/{*objectKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        objectUseCase.deleteObject(tenantId, bucketName, normalizeKey(objectKey));
    }

    /**
     * A trailing-wildcard path variable keeps the leading separator; object keys
     * are stored without it.
     */
    private static String normalizeKey(String rawKey) {
        return rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
    }
}
