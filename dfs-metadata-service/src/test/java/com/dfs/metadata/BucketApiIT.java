package com.dfs.metadata;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BucketApiIT extends AbstractMetadataIT {

    private static final String BUCKETS = "/api/v1/buckets";

    @Test
    void createsBucketWithDefaults() throws Exception {
        mockMvc.perform(put(BUCKETS + "/photos").header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("photos"))
                .andExpect(jsonPath("$.versioningEnabled").value(false))
                .andExpect(jsonPath("$.defaultEncryption").value("NONE"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createsBucketWithExplicitSettings() throws Exception {
        mockMvc.perform(put(BUCKETS + "/archive")
                        .header("X-Tenant-Id", tenantId)
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", true, "defaultEncryption", "AES256"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versioningEnabled").value(true))
                .andExpect(jsonPath("$.defaultEncryption").value("AES256"));
    }

    @Test
    void rejectsDuplicateBucketName() throws Exception {
        mockMvc.perform(put(BUCKETS + "/dupes").header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated());

        mockMvc.perform(put(BUCKETS + "/dupes").header("X-Tenant-Id", tenantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Bucket already exists"));
    }

    @Test
    void isolatesBucketNamesAcrossTenants() throws Exception {
        mockMvc.perform(put(BUCKETS + "/shared-name").header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated());

        mockMvc.perform(put(BUCKETS + "/shared-name")
                        .header("X-Tenant-Id", java.util.UUID.randomUUID()))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidBucketNames() throws Exception {
        for (String invalid : new String[]{"AB", "Uppercase", "has--double", "-leading", "trailing-", "10.0.0.1"}) {
            mockMvc.perform(put(BUCKETS + "/" + invalid).header("X-Tenant-Id", tenantId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
    }

    @Test
    void requiresTenantHeader() throws Exception {
        mockMvc.perform(put(BUCKETS + "/no-tenant"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsAndHeadsExistingBucket() throws Exception {
        mockMvc.perform(put(BUCKETS + "/reports").header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BUCKETS + "/reports").header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("reports"));

        mockMvc.perform(head(BUCKETS + "/reports").header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk());
    }

    @Test
    void returnsNotFoundForUnknownBucket() throws Exception {
        mockMvc.perform(get(BUCKETS + "/missing-bucket").header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void listsBucketsForTenantOnly() throws Exception {
        mockMvc.perform(put(BUCKETS + "/alpha").header("X-Tenant-Id", tenantId));
        mockMvc.perform(put(BUCKETS + "/beta").header("X-Tenant-Id", tenantId));
        mockMvc.perform(put(BUCKETS + "/other").header("X-Tenant-Id", java.util.UUID.randomUUID()));

        mockMvc.perform(get(BUCKETS).header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].name").value("alpha"))
                .andExpect(jsonPath("$.items[1].name").value("beta"));
    }

    @Test
    void togglesVersioning() throws Exception {
        mockMvc.perform(put(BUCKETS + "/toggle").header("X-Tenant-Id", tenantId));

        mockMvc.perform(patch(BUCKETS + "/toggle")
                        .header("X-Tenant-Id", tenantId)
                        .contentType("application/json")
                        .content(json(Map.of("versioningEnabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versioningEnabled").value(true));

        mockMvc.perform(get(BUCKETS + "/toggle").header("X-Tenant-Id", tenantId))
                .andExpect(jsonPath("$.versioningEnabled").value(true));
    }

    @Test
    void rejectsPatchWithMissingField() throws Exception {
        mockMvc.perform(put(BUCKETS + "/patch-invalid").header("X-Tenant-Id", tenantId));

        mockMvc.perform(patch(BUCKETS + "/patch-invalid")
                        .header("X-Tenant-Id", tenantId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.versioningEnabled").isNotEmpty());
    }

    @Test
    void deletesEmptyBucketAndAllowsNameReuse() throws Exception {
        mockMvc.perform(put(BUCKETS + "/temp").header("X-Tenant-Id", tenantId));

        mockMvc.perform(delete(BUCKETS + "/temp").header("X-Tenant-Id", tenantId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BUCKETS + "/temp").header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(BUCKETS + "/temp").header("X-Tenant-Id", tenantId))
                .andExpect(status().isCreated());
    }

    @Test
    void refusesToDeleteNonEmptyBucket() throws Exception {
        mockMvc.perform(put(BUCKETS + "/occupied").header("X-Tenant-Id", tenantId));
        mockMvc.perform(put(BUCKETS + "/occupied/objects/file.txt")
                        .header("X-Tenant-Id", tenantId)
                        .contentType("application/json")
                        .content(json(Map.of(
                                "sizeBytes", 10,
                                "contentHash", "a".repeat(64)))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(BUCKETS + "/occupied").header("X-Tenant-Id", tenantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Bucket not empty"));
    }
}
