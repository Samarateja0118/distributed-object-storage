package com.dfs.blob;

import com.dfs.common.security.DfsJwtProperties;
import com.dfs.common.security.DfsTestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Boots the node against a scratch storage root so tests exercise the real
 * filesystem — staging, atomic publish and fsync are the whole point of this
 * service, and an in-memory stand-in would verify none of them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractBlobNodeIT {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DfsJwtProperties jwtProperties;

    private DfsTestTokens tokens;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("dfs.blob.storage-root-path", STORAGE_ROOT::toString);
    }

    protected String bearer() {
        if (tokens == null) {
            tokens = new DfsTestTokens(jwtProperties);
        }
        return tokens.bearerFor(UUID.randomUUID());
    }

    protected Path storedPathFor(String digest) {
        return STORAGE_ROOT.resolve(digest.substring(0, 2)).resolve(digest.substring(2, 4)).resolve(digest);
    }

    protected long stagingFileCount() throws IOException {
        try (var entries = Files.list(STORAGE_ROOT.resolve(".staging"))) {
            return entries.count();
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("dfs-blob-it-");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create a scratch storage root", e);
        }
    }
}
