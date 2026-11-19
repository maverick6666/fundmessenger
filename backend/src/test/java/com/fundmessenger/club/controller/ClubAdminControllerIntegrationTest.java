package com.fundmessenger.club.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.club.dto.CreateTeamRequest;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.jongtobang.service.NewsCollectorService;
import com.fundmessenger.jongtobang.service.NewsPipelineService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClubAdminControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;

    // ── External service mocks ──
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;
    @MockitoBean NewsPipelineService newsPipelineService;
    @MockitoBean AiCommentBotScheduler aiCommentBotScheduler;
    @MockitoBean SubscriptionScheduler subscriptionScheduler;

    private User executiveUser;
    private User regularUser;
    private User pendingUser;
    private Club club;
    private ClubMembership executiveMembership;
    private ClubMembership regularMembership;
    private ClubMembership pendingMembership;

    @BeforeEach
    void setUp() {
        executiveUser = h.createUser("executive@test.dev");
        regularUser = h.createUser("regular@test.dev");
        pendingUser = h.createUser("pending@test.dev");

        club = h.createClubWithSchool("테스트동아리", "테스트대학교");

        executiveMembership = h.createApprovedMembership(executiveUser, club, "임원홍");
        h.createExecutiveTag(executiveMembership, club);

        regularMembership = h.createApprovedMembership(regularUser, club, "일반김");
        h.createRegularTag(regularMembership, club);

        pendingMembership = h.createPendingMembership(pendingUser, club, "대기박");
    }

    // ========== 1. POST /api/clubs/{id}/admin/teams — 임원 → 201/200 ==========

    @Test
    @DisplayName("임원이 팀을 생성하면 200 응답")
    void createTeam_executive_success() throws Exception {
        CreateTeamRequest request = new CreateTeamRequest();
        request.setName("알파팀");
        request.setDescription("알파팀 설명");

        mockMvc.perform(post("/api/clubs/{clubId}/admin/teams", club.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("알파팀"));
    }

    // ========== 2. POST /api/clubs/{id}/admin/teams — 일반부원 → 403/SecurityException ==========

    @Test
    @DisplayName("일반 부원이 팀 생성 시 403")
    void createTeam_regularMember_forbidden() throws Exception {
        CreateTeamRequest request = new CreateTeamRequest();
        request.setName("불법팀");

        mockMvc.perform(post("/api/clubs/{clubId}/admin/teams", club.getId())
                        .header("Authorization", h.authHeader(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ========== 3. POST /api/clubs/{id}/admin/members/{mid}/tags — 태그 부여 ==========

    @Test
    @DisplayName("임원이 멤버에 태그를 부여하면 200 응답")
    void assignTag_executive_success() throws Exception {
        // 먼저 태그 정의 생성
        String createTagBody = objectMapper.writeValueAsString(
                java.util.Map.of("name", "커스텀태그", "permissionLevel", "REGULAR"));

        String tagResponse = mockMvc.perform(post("/api/clubs/{clubId}/admin/tags", club.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTagBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long tagDefinitionId = objectMapper.readTree(tagResponse).get("id").asLong();

        // 멤버에게 태그 부여
        String assignBody = objectMapper.writeValueAsString(
                java.util.Map.of("tagDefinitionId", tagDefinitionId));

        mockMvc.perform(post("/api/clubs/{clubId}/admin/members/{membershipId}/tags",
                        club.getId(), regularMembership.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isOk());
    }

    // ========== 4. PUT /api/clubs/{id}/admin/join-requests/{mid}/approve — 가입 승인 ==========

    @Test
    @DisplayName("임원이 가입 요청을 승인하면 200 응답")
    void approveJoinRequest_executive_success() throws Exception {
        mockMvc.perform(put("/api/clubs/{clubId}/admin/join-requests/{membershipId}/approve",
                        club.getId(), pendingMembership.getId())
                        .header("Authorization", h.authHeader(executiveUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(pendingMembership.getId()));
    }

    // ========== 5. PUT /api/clubs/{id}/admin/join-requests/{mid}/reject — 가입 거절 ==========

    @Test
    @DisplayName("임원이 가입 요청을 거절하면 200 응답")
    void rejectJoinRequest_executive_success() throws Exception {
        // 별도 pending 유저 생성 (approve와 독립)
        User pendingUser2 = h.createUser("pending2@test.dev");
        ClubMembership pending2 = h.createPendingMembership(pendingUser2, club, "대기이");

        mockMvc.perform(put("/api/clubs/{clubId}/admin/join-requests/{membershipId}/reject",
                        club.getId(), pending2.getId())
                        .header("Authorization", h.authHeader(executiveUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(pending2.getId()));
    }

    // ========== 6. 인증 없이 접근 → 401 ==========

    @Test
    @DisplayName("인증 없이 관리 엔드포인트 접근 시 401 또는 403")
    void allEndpoints_noAuth_rejected() throws Exception {
        // Spring Security: 인증 없이 접근하면 401(Unauthorized) 또는 403(Forbidden) 반환
        mockMvc.perform(get("/api/clubs/{clubId}/admin/join-requests", club.getId()))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/clubs/{clubId}/admin/teams", club.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/clubs/{clubId}/admin/tags", club.getId()))
                .andExpect(status().is4xxClientError());
    }
}
