package com.aman.payment.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Security & Role-Based Access Control")
@AutoConfigureMockMvc
class SecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Swagger OpenAPI docs are publicly accessible without authentication")
    void swaggerUi_isPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Standard authenticated user can access general endpoints")
    void standardUser_canAccessPayments() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer mock-user-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Standard user without FINANCE_ADMIN role is rejected on /api/v1/ledger/reconcile with 403 Forbidden")
    void standardUser_forbiddenOnReconcile() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/reconcile")
                        .header("Authorization", "Bearer mock-user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("User with FINANCE_ADMIN role can access /api/v1/ledger/reconcile")
    void financeAdmin_canAccessReconcile() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/reconcile")
                        .header("Authorization", "Bearer mock-finance-admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API Key with admin privilege can access /api/v1/ledger/reconcile")
    void apiKeyAdmin_canAccessReconcile() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/reconcile")
                        .header("X-API-KEY", "finance-admin-key"))
                .andExpect(status().isOk());
    }
}
