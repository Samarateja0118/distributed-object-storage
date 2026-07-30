package com.dfs.metadata;

import com.dfs.common.security.DfsJwtProperties;
import com.dfs.common.security.DfsTestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Boots the service against a real PostgreSQL instance so Flyway migrations and
 * JPA mappings are verified together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractMetadataIT {

    /**
     * Started once for the whole suite and never stopped: the JUnit
     * {@code @Container} lifecycle would shut this down after the first subclass
     * finishes, leaving later classes with a dead database. Ryuk reaps it when
     * the JVM exits.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DfsJwtProperties jwtProperties;

    private DfsTestTokens tokens;

    /**
     * Each test runs under a fresh tenant, which isolates it without truncating
     * shared tables between cases.
     */
    protected UUID tenantId;

    @BeforeEach
    void assignTenant() {
        tenantId = UUID.randomUUID();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * A real signed token rather than a mocked principal, so the decoder, the
     * issuer check, and the claim names are all exercised — those are exactly the
     * parts that break when issuer and validator drift apart.
     */
    protected String bearer(UUID tenant) {
        if (tokens == null) {
            tokens = new DfsTestTokens(jwtProperties);
        }
        return tokens.bearerFor(tenant);
    }
}
