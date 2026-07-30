package com.dfs.auth.api.rest;

import com.dfs.auth.api.rest.dto.ApiKeyResponse;
import com.dfs.auth.api.rest.dto.CreateApiKeyRequest;
import com.dfs.auth.api.rest.dto.IssuedApiKeyResponse;
import com.dfs.auth.application.port.in.ApiKeyUseCase;
import com.dfs.common.security.DfsTokenClaims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/api-keys")
class ApiKeyController {

    private final ApiKeyUseCase apiKeys;

    ApiKeyController(ApiKeyUseCase apiKeys) {
        this.apiKeys = apiKeys;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedApiKeyResponse createApiKey(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateApiKeyRequest request) {
        return IssuedApiKeyResponse.from(apiKeys.createApiKey(new ApiKeyUseCase.CreateApiKeyCommand(
                DfsTokenClaims.tenantId(jwt), DfsTokenClaims.userId(jwt), request.name(), request.expiresAt())));
    }

    @GetMapping
    List<ApiKeyResponse> listApiKeys(@AuthenticationPrincipal Jwt jwt) {
        return apiKeys.listApiKeys(DfsTokenClaims.tenantId(jwt)).stream().map(ApiKeyResponse::from).toList();
    }

    @DeleteMapping("/{apiKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeApiKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID apiKeyId) {
        apiKeys.revokeApiKey(DfsTokenClaims.tenantId(jwt), apiKeyId);
    }
}
