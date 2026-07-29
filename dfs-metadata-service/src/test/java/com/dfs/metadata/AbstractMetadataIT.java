package com.dfs.metadata;

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
}
