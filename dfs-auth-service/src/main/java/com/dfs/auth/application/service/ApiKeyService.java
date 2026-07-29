package com.dfs.auth.application.service;

import com.dfs.auth.application.port.in.ApiKeyUseCase;
import com.dfs.auth.application.port.out.ApiKeyCachePort;
import com.dfs.auth.application.port.out.ApiKeyRepositoryPort;
import com.dfs.auth.application.port.out.PasswordHasherPort;
import com.dfs.auth.application.port.out.UserRepositoryPort;
import com.dfs.auth.domain.exception.ApiKeyNotFoundException;
import com.dfs.auth.domain.model.ApiKey;
import com.dfs.auth.domain.model.IssuedApiKey;
import com.dfs.auth.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApiKeyService implements ApiKeyUseCase {

    private final ApiKeyRepositoryPort apiKeyRepository;
    private final UserRepositoryPort userRepository;
    private final ApiKeyCachePort apiKeyCache;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public ApiKeyService(
            ApiKeyRepositoryPort apiKeyRepository,
            UserRepositoryPort userRepository,
            ApiKeyCachePort apiKeyCache,
            PasswordHasherPort passwordHasher,
            Clock clock) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.apiKeyCache = apiKeyCache;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public IssuedApiKey createApiKey(CreateApiKeyCommand command) {
        User user = userRepository.findById(command.userId())
                .filter(candidate -> candidate.tenantId().equals(command.tenantId()))
                .filter(User::isActive)
                .orElseThrow(() -> new ApiKeyNotFoundException(command.userId()));

        ApiKeyCodec.GeneratedKey generated = ApiKeyCodec.generate();
        ApiKey saved = apiKeyRepository.save(ApiKey.create(
                user.tenantId(),
                user.id(),
                command.name(),
                generated.prefix(),
                passwordHasher.hash(generated.secret()),
                Instant.now(clock),
                command.expiresAt()));
        return new IssuedApiKey(saved, generated.plaintext());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeys(UUID tenantId) {
        return apiKeyRepository.findAllByTenant(tenantId);
    }

    @Override
    public void revokeApiKey(UUID tenantId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findByTenantAndId(tenantId, apiKeyId)
                .orElseThrow(() -> new ApiKeyNotFoundException(apiKeyId));
        apiKeyRepository.save(apiKey.markRevoked(Instant.now(clock)));
        // Without this the key would keep authenticating until its cache entry
        // expired, which is the whole failure mode revocation exists to prevent.
        apiKeyCache.evict(apiKey.keyPrefix());
    }
}
