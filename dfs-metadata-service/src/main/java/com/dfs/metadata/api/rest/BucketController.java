package com.dfs.metadata.api.rest;

import com.dfs.metadata.api.rest.dto.BucketResponse;
import com.dfs.metadata.api.rest.dto.CreateBucketRequest;
import com.dfs.metadata.api.rest.dto.PageResponse;
import com.dfs.metadata.api.rest.dto.UpdateBucketRequest;
import com.dfs.metadata.application.port.PageQuery;
import com.dfs.metadata.application.port.in.BucketUseCase;
import com.dfs.metadata.domain.model.Bucket;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buckets")
public class BucketController {

    private final BucketUseCase bucketUseCase;

    public BucketController(BucketUseCase bucketUseCase) {
        this.bucketUseCase = bucketUseCase;
    }

    @PutMapping("/{bucketName}")
    public ResponseEntity<BucketResponse> createBucket(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @RequestBody(required = false) CreateBucketRequest request) {
        CreateBucketRequest body = request == null ? new CreateBucketRequest(false, null) : request;
        Bucket bucket = bucketUseCase.createBucket(new BucketUseCase.CreateBucketCommand(
                tenantId, bucketName, body.versioningEnabled(), body.defaultEncryption()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BucketResponse.from(bucket));
    }

    @GetMapping("/{bucketName}")
    public BucketResponse getBucket(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName) {
        return BucketResponse.from(bucketUseCase.getBucket(tenantId, bucketName));
    }

    @GetMapping
    public PageResponse<BucketResponse> listBuckets(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.from(bucketUseCase.listBuckets(tenantId, new PageQuery(page, size)),
                BucketResponse::from);
    }

    @PatchMapping("/{bucketName}")
    public BucketResponse updateBucket(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName,
            @Valid @RequestBody UpdateBucketRequest request) {
        Bucket bucket = bucketUseCase.updateBucket(new BucketUseCase.UpdateBucketCommand(
                tenantId, bucketName, request.versioningEnabled()));
        return BucketResponse.from(bucket);
    }

    @DeleteMapping("/{bucketName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBucket(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String bucketName) {
        bucketUseCase.deleteBucket(tenantId, bucketName);
    }
}
