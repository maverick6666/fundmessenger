package com.fundmessenger.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;

    // Mock external services
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;

    private User superAdmin;
    private User regularUser;
    private Club club;
    private ClubMembership pendingMembership;

    @BeforeEach
    void setUp() {
        superAdmin = h.createSuperAdmin();
        regularUser = h.createUser("regular@test.dev");

        club = h.createClubWithSchool("TestClub", "TestSchool");

        // Create a regular approved membership for regularUser
        ClubMembership approved = h.createApprovedMembership(regularUser, club, "일반부원");
        h.createRegularTag(approved, club);

        // Create a pending membership for another user
        User pendingUser = h.createUser("pending@test.dev");
        pendingMembership = h.createPendingMembership(pendingUser, club, "대기자");
    }

    // ========== 1. Dashboard with SUPER_ADMIN -> 200 ==========

    @Test
    @DisplayName("GET /api/admin/dashboard with SUPER_ADMIN -> 200")
    void getDashboard_asSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", h.authHeader(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSchools").isNumber())
                .andExpect(jsonPath("$.totalClubs").isNumber())
                .andExpect(jsonPath("$.totalUsers").isNumber());
    }

    // ========== 2. Dashboard with regular user -> 403 ==========

    @Test
    @DisplayName("GET /api/admin/dashboard with regular user -> 403")
    void getDashboard_asRegularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isForbidden());
    }

    // ========== 3. Global settings -> 200 ==========

    @Test
    @DisplayName("GET /api/admin/settings with SUPER_ADMIN -> 200")
    void getGlobalSettings_asSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", h.authHeader(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driveAllowedFormats").exists())
                .andExpect(jsonPath("$.driveMaxFileSizeMb").isNumber());
    }

    // ========== 4. Approve membership -> 200 ==========

    @Test
    @DisplayName("POST /api/admin/memberships/{id}/approve with SUPER_ADMIN -> 200")
    void approveMembership_asSuperAdmin_returns200() throws Exception {
        mockMvc.perform(post("/api/admin/memberships/{id}/approve", pendingMembership.getId())
                        .header("Authorization", h.authHeader(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
