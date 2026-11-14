package com.dfs.metadata.api.rest;

import com.dfs.common.security.DfsTokenClaims;
import com.dfs.metadata.api.rest.dto.ChunkPayload;
import com.dfs.metadata.api.rest.dto.CompleteMultipartUploadRequest;
import com.dfs.metadata.api.rest.dto.InitiateMultipartUploadRequest;
import com.dfs.metadata.api.rest.dto.ListPartsResponse;
import com.dfs.metadata.api.rest.dto.MultipartUploadResponse;
import com.dfs.metadata.api.rest.dto.ObjectResponse;
import com.dfs.metadata.api.rest.dto.PartResponse;
import com.dfs.metadata.api.rest.dto.PutPartRequest;
import com.dfs.metadata.application.port.in.MultipartUploadUseCase;
import com.dfs.metadata.domain.model.StorageObject;
import com.dfs.metadata.domain.model.UploadPart;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Multipart upload sessions (ADR 12). This is the control plane only: a client
 * opens, lists, completes and aborts here, and sends each part's bytes to the
 * upload orchestrator, which writes them to blob nodes and registers the part
 * through {@link #registerPart}.
 */
@RestController
@RequestMapping("/api/v1/buckets/{bucketName}/multipart-uploads")
public class MultipartUploadController {

    private final MultipartUploadUseCase uploads;

    public MultipartUploadController(MultipartUploadUseCase uploads) {
        this.uploads = uploads;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MultipartUploadResponse initiate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @Valid @RequestBody InitiateMultipartUploadRequest request) {
        return MultipartUploadResponse.from(uploads.initiate(new MultipartUploadUseCase.InitiateCommand(
                DfsTokenClaims.tenantId(jwt),
                bucketName,
                request.key(),
                request.contentType(),
                request.userMetadata() == null ? Map.of() : request.userMetadata())));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ResponseEntity<PartResponse> registerPart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            @Valid @RequestBody PutPartRequest request) {
        UploadPart part = uploads.registerPart(new MultipartUploadUseCase.RegisterPartCommand(
                DfsTokenClaims.tenantId(jwt),
                bucketName,
                uploadId,
                partNumber,
                request.sizeBytes(),
                request.contentHash(),
                request.chunks().stream().map(ChunkPayload::toDomain).toList()));
        return ResponseEntity.ok()
                .eTag("\"" + part.etag().value() + "\"")
                .body(PartResponse.from(part));
    }

    @GetMapping("/{uploadId}/parts")
    public ListPartsResponse listParts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable UUID uploadId) {
        return ListPartsResponse.from(uploads.listParts(DfsTokenClaims.tenantId(jwt), bucketName, uploadId));
    }

    /**
     * Created rather than OK, because completing is what brings the object into
     * existence — the same status a single PUT answers with.
     */
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<ObjectResponse> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable UUID uploadId,
            @Valid @RequestBody CompleteMultipartUploadRequest request) {
        StorageObject object = uploads.complete(new MultipartUploadUseCase.CompleteCommand(
                DfsTokenClaims.tenantId(jwt),
                bucketName,
                uploadId,
                request.parts().stream()
                        .map(part -> new MultipartUploadUseCase.SelectedPart(part.partNumber(), part.etag()))
                        .toList()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + object.etag() + "\"")
                .header("X-Object-Version-Id", object.versionId().toString())
                .body(ObjectResponse.from(object));
    }

    @DeleteMapping("/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @PathVariable UUID uploadId) {
        uploads.abort(DfsTokenClaims.tenantId(jwt), bucketName, uploadId);
    }
}
