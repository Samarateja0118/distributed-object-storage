package com.dfs.metadata;

import com.dfs.common.hash.Sha256;
import com.dfs.metadata.application.port.in.MultipartUploadUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session expiry (ADR 12), against a clock the test moves rather than waits on.
 *
 * <p>The scheduled sweep is configured not to run during tests; this calls the
 * sweep's use case directly, which is exactly what the scheduler does.
 */
class MultipartExpiryIT extends AbstractMetadataIT {

    private static final String BUCKETS = "/api/v1/buckets";

    /**
     * Started well after real time, so every session the other test classes
     * created with the system clock already looks expired to this one — which is
     * harmless, since each of those classes ran to completion before this one.
     */
    private static final MutableClock CLOCK = new MutableClock(Instant.now().plus(Duration.ofDays(30)));

    @TestConfiguration
    static class ClockOverride {

        @Bean
        @Primary
        MutableClock testClock() {
            return CLOCK;
        }
    }

    @Autowired
    private MultipartUploadUseCase uploads;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID initiate(String bucket) throws Exception {
        String response = mockMvc.perform(post(BUCKETS + "/" + bucket + "/multipart-uploads")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("key", "clip.mp4"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("uploadId").asText());
    }

    private int sessionRows(UUID uploadId) {
        return jdbc.queryForObject("SELECT count(*) FROM multipart_uploads WHERE id = ?", Integer.class, uploadId);
    }

    private static Map<String, Object> onePart() {
        String digest = "a".repeat(64);
        return Map.of(
                "sizeBytes", 10,
                "contentHash", Sha256.composite(List.of(digest)),
                "chunks", List.of(Map.of("index", 0, "digest", digest, "sizeBytes", 10, "nodeId", "blob-node-0")));
    }

    @Test
    @DisplayName("an expired session is refused at once, then deleted by the sweep with its staged rows")
    void expiredSessionsAreRefusedThenSwept() throws Exception {
        mockMvc.perform(put(BUCKETS + "/media")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", false))))
                .andExpect(status().isCreated());
        UUID stale = initiate("media");
        mockMvc.perform(put(BUCKETS + "/media/multipart-uploads/" + stale + "/parts/1")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(onePart())))
                .andExpect(status().isOk());

        CLOCK.advance(Duration.ofHours(24).plusSeconds(1));
        UUID fresh = initiate("media");

        // Refused from the moment it expires, before any sweep has run: whether
        // a late part is accepted never depends on the sweep's schedule.
        mockMvc.perform(put(BUCKETS + "/media/multipart-uploads/" + stale + "/parts/2")
                        .header("Authorization", bearer(tenantId))
                        .contentType("application/json")
                        .content(json(onePart())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BUCKETS + "/media/multipart-uploads/" + stale + "/parts")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isNotFound());
        assertThat(sessionRows(stale)).isOne();

        int deleted = 0;
        int batch;
        do {
            batch = uploads.expireStaleUploads();
            deleted += batch;
        } while (batch > 0);

        assertThat(deleted).isPositive();
        assertThat(sessionRows(stale)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM multipart_upload_chunks WHERE upload_id = ?", Integer.class, stale)).isZero();

        // A session that has not reached its expiry is left alone.
        assertThat(sessionRows(fresh)).isOne();
        mockMvc.perform(get(BUCKETS + "/media/multipart-uploads/" + fresh + "/parts")
                        .header("Authorization", bearer(tenantId)))
                .andExpect(status().isOk());
    }
}
