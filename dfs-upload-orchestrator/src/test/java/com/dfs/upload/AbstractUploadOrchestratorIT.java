package com.dfs.upload;

import com.dfs.common.security.DfsJwtProperties;
import com.dfs.common.security.DfsTestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Boots the orchestrator against in-process stand-ins for the blob node and the
 * metadata service.
 *
 * <p>The stub is started once for the whole suite rather than per class, for the
 * same reason the metadata service's containers are: the JUnit-managed
 * lifecycle would tear it down after the first subclass and leave later classes
 * pointing at a closed socket.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractUploadOrchestratorIT {

    protected static final DownstreamStub DOWNSTREAM = new DownstreamStub();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DfsJwtProperties jwtProperties;

    private DfsTestTokens tokens;

    protected UUID tenantId;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("dfs.upload.blob-base-url", DOWNSTREAM::baseUrl);
        registry.add("dfs.upload.metadata-base-url", DOWNSTREAM::baseUrl);
    }

    @BeforeEach
    void resetDownstream() {
        DOWNSTREAM.reset();
        tenantId = UUID.randomUUID();
    }

    /**
     * A real signed token rather than a mocked principal: these tests assert on
     * the exact string forwarded downstream, which only means something if it is
     * the same token the resource server validated.
     */
    protected String bearer() {
        if (tokens == null) {
            tokens = new DfsTestTokens(jwtProperties);
        }
        return tokens.bearerFor(tenantId);
    }
}
