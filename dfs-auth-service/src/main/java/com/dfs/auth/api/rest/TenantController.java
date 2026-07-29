package com.dfs.auth.api.rest;

import com.dfs.auth.api.rest.dto.CreateUserRequest;
import com.dfs.auth.api.rest.dto.RegisterTenantRequest;
import com.dfs.auth.api.rest.dto.TenantRegistrationResponse;
import com.dfs.auth.api.rest.dto.TenantResponse;
import com.dfs.auth.api.rest.dto.UserResponse;
import com.dfs.auth.application.port.in.TenantUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class TenantController {

    private final TenantUseCase tenants;

    TenantController(TenantUseCase tenants) {
        this.tenants = tenants;
    }

    /**
     * The one write that cannot require a token: it is what produces the first
     * account capable of getting one.
     */
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    TenantRegistrationResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return TenantRegistrationResponse.from(tenants.registerTenant(new TenantUseCase.RegisterTenantCommand(
                request.name(), request.slug(), request.ownerEmail(), request.ownerPassword())));
    }

    @GetMapping("/tenant")
    TenantResponse getOwnTenant(@AuthenticationPrincipal Jwt jwt) {
        return TenantResponse.from(tenants.getTenant(TenantClaims.tenantId(jwt)));
    }

    @PostMapping("/tenant/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse addUser(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest request) {
        UUID tenantId = TenantClaims.tenantId(jwt);
        return UserResponse.from(tenants.addUser(
                new TenantUseCase.AddUserCommand(tenantId, request.email(), request.password())));
    }

    @GetMapping("/tenant/users")
    List<UserResponse> listUsers(@AuthenticationPrincipal Jwt jwt) {
        return tenants.listUsers(TenantClaims.tenantId(jwt)).stream().map(UserResponse::from).toList();
    }
}
