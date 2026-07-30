package com.dfs.blob.api.rest;

import com.dfs.blob.api.rest.dto.BlobResponse;
import com.dfs.blob.application.port.in.BlobStorageUseCase;
import com.dfs.blob.domain.exception.BlobNotFoundException;
import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * Internal data-plane API. Blobs are addressed purely by content digest, so there
 * is no tenant in these paths — the same bytes uploaded by two tenants are one
 * blob. Tenant ownership is tracked by the metadata service, which is what makes
 * this node safe to deduplicate across tenants.
 */
@RestController
@RequestMapping("/internal/v1/blobs")
class BlobController {

    private final BlobStorageUseCase blobs;

    BlobController(BlobStorageUseCase blobs) {
        this.blobs = blobs;
    }

    /**
     * Idempotent by construction: the digest names the content, so re-sending the
     * same bytes is indistinguishable from the first write and returns the same
     * result.
     */
    @PutMapping("/{digest}")
    ResponseEntity<BlobResponse> store(@PathVariable String digest, InputStream body) {
        StoredBlob stored = blobs.store(new BlobDigest(digest), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(BlobResponse.from(stored));
    }

    /**
     * Also serves HEAD, which Spring answers with these headers and no body.
     */
    @GetMapping("/{digest}")
    ResponseEntity<InputStreamResource> read(@PathVariable String digest) {
        BlobDigest blobDigest = new BlobDigest(digest);
        StoredBlob stored = blobs.stat(blobDigest).orElseThrow(() -> new BlobNotFoundException(blobDigest));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(stored.sizeBytes())
                .header(HttpHeaders.ETAG, "\"" + stored.digest().value() + "\"")
                .body(new InputStreamResource(blobs.open(blobDigest)));
    }
}
