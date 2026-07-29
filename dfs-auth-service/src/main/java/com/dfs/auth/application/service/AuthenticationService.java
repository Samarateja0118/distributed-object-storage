package com.dfs.auth.application.service;

import com.dfs.auth.application.port.in.AuthenticationUseCase;
import com.dfs.auth.application.port.out.ApiKeyCachePort;
import com.dfs.auth.application.port.out.ApiKeyRepositoryPort;
import com.dfs.auth.application.port.out.PasswordHasherPort;
import com.dfs.auth.application.port.out.TokenIssuerPort;
import com.dfs.auth.application.port.out.UserRepositoryPort;
import com.dfs.auth.domain.exception.AuthValidationException;
import com.dfs.auth.domain.exception.InvalidCredentialsException;
import com.dfs.auth.domain.model.AccessToken;
import com.dfs.auth.domain.model.ApiKey;
import com.dfs.auth.domain.model.EmailAddress;
import com.dfs.auth.domain.model.Principal;
import com.dfs.auth.domain.model.User;
import com.dfs.auth.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthenticationService implements AuthenticationUseCase {

    /**
     * Compared against when no user matches, so a missing account costs the same
     * time as a wrong password and cannot be detected by timing.
     */
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa1u5hK5kX1nD1YQq8Uu6bJZQ3pQVQ0S";

    private final UserRepositoryPort userRepository;
    private final ApiKeyRepositoryPort apiKeyRepository;
    private final ApiKeyCachePort apiKeyCache;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final Clock clock;

    public AuthenticationService(
            UserRepositoryPort userRepository,
            ApiKeyRepositoryPort apiKeyRepository,
            ApiKeyCachePort apiKeyCache,
            PasswordHasherPort passwordHasher,
            TokenIssuerPort tokenIssuer,
            Clock clock) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyCache = apiKeyCache;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    public AccessToken login(String email, String password) {
        Optional<User> user = findUserForLogin(email);
        if (user.isEmpty()) {
            passwordHasher.matches(password == null ? "" : password, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        if (password == null || !passwordHasher.matches(password, user.get().passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issue(Principal.of(user.get()));
    }

    @Override
    public AccessToken exchangeApiKey(String plaintextKey) {
        return tokenIssuer.issue(verifyApiKey(plaintextKey));
    }

    @Override
    public Principal verifyApiKey(String plaintextKey) {
        ApiKeyCodec.ParsedKey parsed = ApiKeyCodec.parse(plaintextKey);
        String digest = ApiKeyCodec.sha256(plaintextKey);

        Optional<Principal> cached = apiKeyCache.find(parsed.prefix())
                .filter(entry -> ApiKeyCodec.constantTimeEquals(entry.secretSha256(), digest))
                .map(AuthenticationService::toPrincipal);
        if (cached.isPresent()) {
            return cached.get();
        }

        ApiKey apiKey = apiKeyRepository.findByPrefix(parsed.prefix())
                .filter(candidate -> candidate.isUsableAt(Instant.now(clock)))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordHasher.matches(parsed.secret(), apiKey.secretHash())) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findById(apiKey.userId()).orElseThrow(InvalidCredentialsException::new);

        Principal principal = Principal.of(user);
        apiKeyCache.put(parsed.prefix(), new ApiKeyCachePort.CachedApiKey(
                digest, principal.tenantId(), principal.userId(), principal.email().value(),
                principal.role().name()));
        apiKeyRepository.touchLastUsed(apiKey.id(), Instant.now(clock));
        return principal;
    }

    /**
     * A malformed address is a failed credential, not a validation error: telling
     * the caller their input was unparseable is more than an unauthenticated
     * client should learn.
     */
    private Optional<User> findUserForLogin(String email) {
        try {
            return userRepository.findByEmail(new EmailAddress(email)).filter(User::isActive);
        } catch (AuthValidationException e) {
            return Optional.empty();
        }
    }

    private static Principal toPrincipal(ApiKeyCachePort.CachedApiKey entry) {
        return new Principal(
                entry.tenantId(),
                entry.userId(),
                new EmailAddress(entry.email()),
                UserRole.valueOf(entry.role()));
    }
}
