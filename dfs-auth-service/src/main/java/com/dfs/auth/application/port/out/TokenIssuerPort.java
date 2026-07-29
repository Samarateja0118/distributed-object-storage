package com.dfs.auth.application.port.out;

import com.dfs.auth.domain.model.AccessToken;
import com.dfs.auth.domain.model.Principal;

/**
 * Outbound port for minting signed access tokens.
 */
public interface TokenIssuerPort {

    AccessToken issue(Principal principal);
}
