package com.shareride.auth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareride.auth.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link com.shareride.auth.controller.AdminBloomFilterController} security.
 *
 * <p>Verifies end-to-end security gating with full Spring Boot context, Testcontainers (PostgreSQL,
 * Valkey), and mock gateway headers simulating authenticated callers with different roles.
 */
class AdminBloomFilterSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void rebuildBloomFilter_withoutHeaders_returns401Unauthorized() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/rebuild")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rebuildBloomFilter_withRiderRole_returns403Forbidden() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/rebuild")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "018d9f1e-8742-7ef1-95c2-f123456789ab")
                                .header("X-User-Email", "rider@shareride.com")
                                .header("X-User-Roles", "RIDER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rebuildBloomFilter_withAdminRole_returns200Ok() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/rebuild")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "018d9f1e-8742-7ef1-95c2-a987654321ba")
                                .header("X-User-Email", "admin@shareride.com")
                                .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void initializeBloomFilter_withoutHeaders_returns401Unauthorized() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/initialize")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void initializeBloomFilter_withDriverRole_returns403Forbidden() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/initialize")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "018d9f1e-8742-7ef1-95c2-f123456789cd")
                                .header("X-User-Email", "driver@shareride.com")
                                .header("X-User-Roles", "DRIVER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void initializeBloomFilter_withAdminRole_returns200Ok() throws Exception {
        mockMvc.perform(
                        post("/auth/v1/admin/bloom-filter/initialize")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "018d9f1e-8742-7ef1-95c2-a987654321ba")
                                .header("X-User-Email", "admin@shareride.com")
                                .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
