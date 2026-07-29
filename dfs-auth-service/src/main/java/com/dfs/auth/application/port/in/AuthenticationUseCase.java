package com.dfs.auth.application.port.in;

import com.dfs.auth.domain.model.AccessToken;
import com.dfs.auth.domain.model.Principal;

/**
 * Inbound port for turning credentials into a signed token.
 */
public interface AuthenticationUseCase {

    AccessToken login(String email, String password);

    /**
     * Exchanges an API key for a short-lived token, so downstream services only
     * ever have to validate one credential format.
     */
    AccessToken exchangeApiKey(String plaintextKey);

    Principal verifyApiKey(String plaintextKey);
}
