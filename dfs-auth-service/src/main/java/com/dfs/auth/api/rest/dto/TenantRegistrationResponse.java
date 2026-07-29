package com.dfs.auth.api.rest.dto;

import com.dfs.auth.application.port.in.TenantUseCase.TenantRegistration;

public record TenantRegistrationResponse(
        TenantResponse tenant,
        UserResponse owner) {

    public static TenantRegistrationResponse from(TenantRegistration registration) {
        return new TenantRegistrationResponse(
                TenantResponse.from(registration.tenant()), UserResponse.from(registration.owner()));
    }
}
