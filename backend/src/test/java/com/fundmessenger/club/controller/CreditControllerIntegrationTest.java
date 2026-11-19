package com.fundmessenger.club.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.club.dto.AllocateCreditRequest;
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
class CreditControllerIntegrationTest {

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
    private Club club;
    private Team team;

    @BeforeEach
    void setUp() {
        executiveUser = h.createUser("exec-credit@test.dev");
        regularUser = h.createUser("reg-credit@test.dev");

        club = h.createClubWithSchool("크레딧테스트클럽", "크레딧대학교");

        ClubMembership execMembership = h.createApprovedMembership(executiveUser, club, "임원이");
        h.createExecutiveTag(execMembership, club);

        ClubMembership regMembership = h.createApprovedMembership(regularUser, club, "일반이");
        h.createRegularTag(regMembership, club);

        team = h.createTeam(club, "투자1팀");
    }

    // ========== 1. POST /api/clubs/{id}/credits/allocate → 200 ==========

    @Test
    @DisplayName("임원이 팀에 크레딧 분배하면 200 응답")
    void allocate_executive_success() throws Exception {
        AllocateCreditRequest request = new AllocateCreditRequest();
        request.setTeamId(team.getId());
        request.setAmount(100);
        request.setDescription("투자1팀 분배");

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubBalance").value(900))
                .andExpect(jsonPath("$.totalAllocated").value(100));
    }

    // ========== 2. POST /api/clubs/{id}/credits/allocate (잔액 부족) → 400 ==========

    @Test
    @DisplayName("잔액 초과 분배 요청 시 400 응답")
    void allocate_insufficientBalance_badRequest() throws Exception {
        AllocateCreditRequest request = new AllocateCreditRequest();
        request.setTeamId(team.getId());
        request.setAmount(99999); // club balance is 1000
        request.setDescription("과다 분배");

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========== 3. POST /api/clubs/{id}/credits/allocate (일반부원) → 403 ==========

    @Test
    @DisplayName("일반 부원이 크레딧 분배 시 403")
    void allocate_regularMember_forbidden() throws Exception {
        AllocateCreditRequest request = new AllocateCreditRequest();
        request.setTeamId(team.getId());
        request.setAmount(50);

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ========== 4. GET /api/clubs/{id}/credits/summary → 200 ==========

    @Test
    @DisplayName("멤버가 크레딧 요약을 조회하면 200 응답")
    void getSummary_member_success() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/credits/summary", club.getId())
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubBalance").value(1000))
                .andExpect(jsonPath("$.teams").isArray());
    }

    // ========== 5. GET /api/clubs/{id}/credits/history → 200 ==========

    @Test
    @DisplayName("멤버가 크레딧 내역을 조회하면 200 응답")
    void getHistory_member_success() throws Exception {
        // 먼저 분배를 하나 실행하여 내역 생성
        AllocateCreditRequest request = new AllocateCreditRequest();
        request.setTeamId(team.getId());
        request.setAmount(50);
        request.setDescription("내역 테스트");

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(executiveUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 내역 조회
        mockMvc.perform(get("/api/clubs/{clubId}/credits/history", club.getId())
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].amount").value(50));
    }
}
