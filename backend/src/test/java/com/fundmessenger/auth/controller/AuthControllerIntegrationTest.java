package com.fundmessenger.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.jongtobang.service.NewsCollectorService;
import com.fundmessenger.jongtobang.service.StockMasterService;
import com.fundmessenger.jongtobang.service.StockPriceService;
import com.fundmessenger.support.TestHelper;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.environment=development")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;

    // Mock external services
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;

    private User regularUser;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        regularUser = h.createUser("regular@test.dev");
        superAdmin = h.createSuperAdmin();
    }

    @Test
    @DisplayName("POST /api/auth/dev-login with DEV_1 -> 200 + token + userStatus")
    void devLogin_withDev1_returns200WithTokenAndStatus() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "DEV_1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.userStatus").isString());
    }

    @Test
    @DisplayName("POST /api/auth/dev-login with SUPER_ADMIN -> 200 + superAdmin=true")
    void devLogin_withSuperAdmin_returnsSuperAdminTrue() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.superAdmin").value(true))
                .andExpect(jsonPath("$.userStatus").value("SUPER_ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/dev-login with empty role -> 400")
    void devLogin_withEmptyRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/me with valid JWT -> 200 + userId")
    void getMe_withValidJwt_returns200WithUserId() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(regularUser.getId()))
                .andExpect(jsonPath("$.email").value(regularUser.getEmail()));
    }

    @Test
    @DisplayName("GET /api/auth/me without JWT -> 401")
    void getMe_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
