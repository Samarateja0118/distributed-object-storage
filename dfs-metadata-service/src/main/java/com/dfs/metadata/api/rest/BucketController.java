package com.dfs.metadata.api.rest;

import com.dfs.common.security.DfsTokenClaims;
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

@RestController
@RequestMapping("/api/v1/buckets")
public class BucketController {

    private final BucketUseCase bucketUseCase;

    public BucketController(BucketUseCase bucketUseCase) {
        this.bucketUseCase = bucketUseCase;
    }

    @PutMapping("/{bucketName}")
    public ResponseEntity<BucketResponse> createBucket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @RequestBody(required = false) CreateBucketRequest request) {
        CreateBucketRequest body = request == null ? new CreateBucketRequest(false, null) : request;
        Bucket bucket = bucketUseCase.createBucket(new BucketUseCase.CreateBucketCommand(
                DfsTokenClaims.tenantId(jwt), bucketName, body.versioningEnabled(), body.defaultEncryption()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BucketResponse.from(bucket));
    }

    @GetMapping("/{bucketName}")
    public BucketResponse getBucket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName) {
        return BucketResponse.from(bucketUseCase.getBucket(DfsTokenClaims.tenantId(jwt), bucketName));
    }

    @GetMapping
    public PageResponse<BucketResponse> listBuckets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.from(bucketUseCase.listBuckets(DfsTokenClaims.tenantId(jwt), new PageQuery(page, size)),
                BucketResponse::from);
    }

    @PatchMapping("/{bucketName}")
    public BucketResponse updateBucket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName,
            @Valid @RequestBody UpdateBucketRequest request) {
        Bucket bucket = bucketUseCase.updateBucket(new BucketUseCase.UpdateBucketCommand(
                DfsTokenClaims.tenantId(jwt), bucketName, request.versioningEnabled()));
        return BucketResponse.from(bucket);
    }

    @DeleteMapping("/{bucketName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBucket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bucketName) {
        bucketUseCase.deleteBucket(DfsTokenClaims.tenantId(jwt), bucketName);
    }
}
