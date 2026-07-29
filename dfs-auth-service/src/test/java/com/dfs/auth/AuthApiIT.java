package com.dfs.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIT extends AbstractAuthIT {

    private static final String TENANTS = "/api/v1/tenants";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String TOKEN = "/api/v1/auth/token";
    private static final String API_KEYS = "/api/v1/tenant/api-keys";
    private static final String PASSWORD = "correct-horse-battery";

    /** Keeps slugs and emails unique without truncating tables between tests. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void registersATenantWithItsOwner() throws Exception {
        String slug = uniqueSlug();
        mockMvc.perform(post(TENANTS).contentType("application/json")
                        .content(json(registration(slug))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.slug").value(slug))
                .andExpect(jsonPath("$.tenant.id").isNotEmpty())
                .andExpect(jsonPath("$.owner.email").value(ownerEmail(slug)))
                .andExpect(jsonPath("$.owner.role").value("OWNER"));
    }

    @Test
    void neverReturnsPasswordMaterial() throws Exception {
        String slug = uniqueSlug();
        MvcResult result = mockMvc.perform(post(TENANTS).contentType("application/json")
                        .content(json(registration(slug))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    void rejectsADuplicateSlug() throws Exception {
        String slug = uniqueSlug();
        mockMvc.perform(post(TENANTS).contentType("application/json").content(json(registration(slug))))
                .andExpect(status().isCreated());

        Map<String, Object> duplicate = registration(slug);
        duplicate.put("ownerEmail", "other-" + ownerEmail(slug));
        mockMvc.perform(post(TENANTS).contentType("application/json").content(json(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsADuplicateEmail() throws Exception {
        String slug = uniqueSlug();
        mockMvc.perform(post(TENANTS).contentType("application/json").content(json(registration(slug))))
                .andExpect(status().isCreated());

        Map<String, Object> duplicate = registration(uniqueSlug());
        duplicate.put("ownerEmail", ownerEmail(slug));
        mockMvc.perform(post(TENANTS).contentType("application/json").content(json(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRegistrationPayloads() throws Exception {
        Map<String, Object> invalid = registration(uniqueSlug());
        invalid.put("slug", "No Spaces Allowed");
        invalid.put("ownerPassword", "short");
        mockMvc.perform(post(TENANTS).contentType("application/json").content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").isNotEmpty())
                .andExpect(jsonPath("$.errors.ownerPassword").isNotEmpty());
    }

    @Test
    void issuesATokenForValidCredentials() throws Exception {
        String slug = uniqueSlug();
        register(slug);

        mockMvc.perform(post(LOGIN).contentType("application/json")
                        .content(json(Map.of("email", ownerEmail(slug), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void rejectsAWrongPasswordAndAnUnknownAccountIdentically() throws Exception {
        String slug = uniqueSlug();
        register(slug);

        MvcResult wrongPassword = mockMvc.perform(post(LOGIN).contentType("application/json")
                        .content(json(Map.of("email", ownerEmail(slug), "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownUser = mockMvc.perform(post(LOGIN).contentType("application/json")
                        .content(json(Map.of("email", "nobody@example.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Identical bodies are the point: a difference here would let an attacker
        // enumerate which accounts exist.
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownUser.getResponse().getContentAsString());
    }

    @Test
    void malformedEmailFailsAsCredentialsNotValidation() throws Exception {
        mockMvc.perform(post(LOGIN).contentType("application/json")
                        .content(json(Map.of("email", "not-an-email", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresATokenForTenantEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tenant")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(API_KEYS)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAGarbageToken() throws Exception {
        mockMvc.perform(get("/api/v1/tenant").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolvesTheCallersOwnTenantFromTheToken() throws Exception {
        String slug = uniqueSlug();
        JsonNode registered = register(slug);
        String token = login(ownerEmail(slug));

        mockMvc.perform(get("/api/v1/tenant").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.id").value(registered.at("/tenant/id").asText()));
    }

    @Test
    void addsAndListsUsersWithinTheCallersTenant() throws Exception {
        String slug = uniqueSlug();
        register(slug);
        String token = login(ownerEmail(slug));
        String memberEmail = "member-" + ownerEmail(slug);

        mockMvc.perform(post("/api/v1/tenant/users").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json(Map.of("email", memberEmail, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(get("/api/v1/tenant/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void issuesAnApiKeyExactlyOnce() throws Exception {
        String slug = uniqueSlug();
        register(slug);
        String token = login(ownerEmail(slug));

        JsonNode created = objectMapper.readTree(mockMvc.perform(post(API_KEYS)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json(Map.of("name", "ci-pipeline"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plaintextKey").isNotEmpty())
                .andReturn().getResponse().getContentAsString());

        String plaintext = created.get("plaintextKey").asText();
        assertThat(plaintext).startsWith("dfs_");

        // Listing must never expose the secret again.
        mockMvc.perform(get(API_KEYS).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ci-pipeline"))
                .andExpect(jsonPath("$[0].plaintextKey").doesNotExist())
                .andExpect(jsonPath("$[0].secretHash").doesNotExist());
    }

    @Test
    void exchangesAnApiKeyForAToken() throws Exception {
        String slug = uniqueSlug();
        register(slug);
        String plaintext = createApiKey(login(ownerEmail(slug)));

        String exchanged = objectMapper.readTree(mockMvc.perform(post(TOKEN).contentType("application/json")
                        .content(json(Map.of("apiKey", plaintext))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        // The exchanged token must be usable exactly like a login token.
        mockMvc.perform(get("/api/v1/tenant").header("Authorization", "Bearer " + exchanged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug));
    }

    @Test
    void rejectsAMalformedOrUnknownApiKey() throws Exception {
        mockMvc.perform(post(TOKEN).contentType("application/json")
                        .content(json(Map.of("apiKey", "totally-bogus"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(TOKEN).contentType("application/json")
                        .content(json(Map.of("apiKey", "dfs_%s_%s".formatted("aabbccddeeff", "00".repeat(32))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stopsAcceptingARevokedApiKey() throws Exception {
        String slug = uniqueSlug();
        register(slug);
        String token = login(ownerEmail(slug));
        String plaintext = createApiKey(token);

        // Exchange once so the key is sitting in the Redis cache; revocation has
        // to evict it, otherwise it would keep working until the TTL expired.
        mockMvc.perform(post(TOKEN).contentType("application/json")
                        .content(json(Map.of("apiKey", plaintext))))
                .andExpect(status().isOk());

        String apiKeyId = objectMapper.readTree(mockMvc.perform(get(API_KEYS)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(delete(API_KEYS + "/" + apiKeyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(TOKEN).contentType("application/json")
                        .content(json(Map.of("apiKey", plaintext))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingAnotherTenantsKeyIsNotFound() throws Exception {
        String victimSlug = uniqueSlug();
        register(victimSlug);
        String victimToken = login(ownerEmail(victimSlug));
        createApiKey(victimToken);
        String victimKeyId = objectMapper.readTree(mockMvc.perform(get(API_KEYS)
                        .header("Authorization", "Bearer " + victimToken))
                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        String attackerSlug = uniqueSlug();
        register(attackerSlug);
        String attackerToken = login(ownerEmail(attackerSlug));

        mockMvc.perform(delete(API_KEYS + "/" + victimKeyId).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // And the victim's listing is untouched by the attempt.
        mockMvc.perform(get(API_KEYS).header("Authorization", "Bearer " + victimToken))
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist());
    }

    @Test
    void apiKeysAreScopedToTheirTenant() throws Exception {
        String slug = uniqueSlug();
        register(slug);
        createApiKey(login(ownerEmail(slug)));

        String otherSlug = uniqueSlug();
        register(otherSlug);

        mockMvc.perform(get(API_KEYS).header("Authorization", "Bearer " + login(ownerEmail(otherSlug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String createApiKey(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(API_KEYS)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json(Map.of("name", "ci-pipeline"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .get("plaintextKey").asText();
    }

    private JsonNode register(String slug) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(TENANTS).contentType("application/json")
                        .content(json(registration(slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(LOGIN).contentType("application/json")
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private static Map<String, Object> registration(String slug) {
        return new java.util.HashMap<>(Map.of(
                "name", "Tenant " + slug,
                "slug", slug,
                "ownerEmail", ownerEmail(slug),
                "ownerPassword", PASSWORD));
    }

    private static String ownerEmail(String slug) {
        return "owner@%s.example.com".formatted(slug);
    }

    private static String uniqueSlug() {
        return "t%d-%s".formatted(
                SEQUENCE.incrementAndGet(), UUID.randomUUID().toString().substring(0, 8));
    }
}
