package com.dfs.auth.api.rest;

import com.dfs.auth.api.rest.dto.ApiKeyExchangeRequest;
import com.dfs.auth.api.rest.dto.LoginRequest;
import com.dfs.auth.api.rest.dto.TokenResponse;
import com.dfs.auth.application.port.in.AuthenticationUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {

    private final AuthenticationUseCase authentication;

    AuthenticationController(AuthenticationUseCase authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authentication.login(request.email(), request.password()));
    }

    /**
     * Trades a long-lived API key for a short-lived token so that services
     * downstream only ever have to validate one credential format.
     */
    @PostMapping("/token")
    TokenResponse exchangeApiKey(@Valid @RequestBody ApiKeyExchangeRequest request) {
        return TokenResponse.from(authentication.exchangeApiKey(request.apiKey()));
    }
}
