package com.fundmessenger.fund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.fund.dto.*;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FundControllerIntegrationTest {

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

    private User leaderUser;
    private User memberUser;
    private Club club;
    private Team fundTeam;
    private ClubMembership leaderMembership;
    private ClubMembership memberMembership;

    @BeforeEach
    void setUp() {
        leaderUser = h.createUser("fund-leader@test.dev");
        memberUser = h.createUser("fund-member@test.dev");

        club = h.createClubWithSchool("펀드테스트클럽", "펀드대학교");

        fundTeam = h.createFundTeam(club, "알파펀드팀");

        leaderMembership = h.createApprovedMembership(leaderUser, club, "팀장이");
        h.createExecutiveTag(leaderMembership, club);
        h.createTeamLeaderTag(leaderMembership, club, fundTeam);

        memberMembership = h.createApprovedMembership(memberUser, club, "팀원이");
        h.createRegularTag(memberMembership, club);
        h.createTeamMemberTag(memberMembership, club, fundTeam);
    }

    private String basePath() {
        return "/api/clubs/" + club.getId() + "/fund/teams/" + fundTeam.getId();
    }

    // ================================================================
    // PositionController Tests
    // ================================================================

    @Test
    @DisplayName("매니저가 포지션을 생성하면 200 응답")
    void createPosition_leader_success() throws Exception {
        CreatePositionRequest request = new CreatePositionRequest();
        request.setTicker("005930");
        request.setTickerName("삼성전자");
        request.setMarket("KRX");
        request.setBuyPrice(new BigDecimal("70000"));
        request.setQuantity(new BigDecimal("10"));

        mockMvc.perform(post(basePath() + "/positions")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("005930"))
                .andExpect(jsonPath("$.tickerName").value("삼성전자"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("팀원이 포지션 목록을 조회하면 200 응답")
    void getPositions_teamMember_success() throws Exception {
        // 먼저 포지션 생성
        CreatePositionRequest request = new CreatePositionRequest();
        request.setTicker("005930");
        request.setTickerName("삼성전자");
        request.setMarket("KRX");
        request.setBuyPrice(new BigDecimal("70000"));
        request.setQuantity(new BigDecimal("10"));

        mockMvc.perform(post(basePath() + "/positions")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 팀원이 조회
        mockMvc.perform(get(basePath() + "/positions")
                        .header("Authorization", h.authHeader(memberUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ticker").value("005930"));
    }

    // ================================================================
    // TradeRequestController Tests
    // ================================================================

    @Test
    @DisplayName("팀원이 매수 요청을 생성하면 200 응답")
    void createBuyRequest_teamMember_success() throws Exception {
        CreateBuyRequest request = new CreateBuyRequest();
        request.setTargetTicker("035720");
        request.setTargetMarket("KRX");
        request.setTargetTickerName("카카오");
        request.setOrderQuantity(new BigDecimal("5"));
        request.setBuyPrice(new BigDecimal("50000"));
        request.setMemo("성장성 기대 매수");

        mockMvc.perform(post(basePath() + "/requests/buy")
                        .header("Authorization", h.authHeader(memberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTicker").value("035720"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("매니저가 매수 요청을 승인하면 200 응답 + APPROVED")
    void approveBuyRequest_leader_success() throws Exception {
        // 매수 요청 먼저 생성
        CreateBuyRequest buyReq = new CreateBuyRequest();
        buyReq.setTargetTicker("035720");
        buyReq.setTargetMarket("KRX");
        buyReq.setTargetTickerName("카카오");
        buyReq.setOrderQuantity(new BigDecimal("5"));
        buyReq.setBuyPrice(new BigDecimal("50000"));
        buyReq.setMemo("성장성 기대");

        MvcResult createResult = mockMvc.perform(post(basePath() + "/requests/buy")
                        .header("Authorization", h.authHeader(memberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isOk())
                .andReturn();

        Long requestId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // 매니저가 승인
        ApproveRequest approveReq = new ApproveRequest();
        approveReq.setExecutedPrice(new BigDecimal("50000"));
        approveReq.setExecutedQuantity(new BigDecimal("5"));

        mockMvc.perform(post(basePath() + "/requests/{requestId}/approve", requestId)
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("팀원이 매도 요청을 생성하면 200 응답")
    void createSellRequest_teamMember_success() throws Exception {
        // 포지션 먼저 생성
        CreatePositionRequest posReq = new CreatePositionRequest();
        posReq.setTicker("005930");
        posReq.setTickerName("삼성전자");
        posReq.setMarket("KRX");
        posReq.setBuyPrice(new BigDecimal("70000"));
        posReq.setQuantity(new BigDecimal("10"));

        MvcResult posResult = mockMvc.perform(post(basePath() + "/positions")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(posReq)))
                .andExpect(status().isOk())
                .andReturn();

        Long positionId = objectMapper.readTree(posResult.getResponse().getContentAsString())
                .get("id").asLong();

        // 매도 요청 생성
        CreateSellRequest sellReq = new CreateSellRequest();
        sellReq.setPositionId(positionId);
        sellReq.setSellQuantity(new BigDecimal("5"));
        sellReq.setSellPrice(new BigDecimal("75000"));
        sellReq.setMemo("일부 이익실현");

        mockMvc.perform(post(basePath() + "/requests/sell")
                        .header("Authorization", h.authHeader(memberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ================================================================
    // DecisionNoteController Tests
    // ================================================================

    @Test
    @DisplayName("매니저가 의사결정 노트를 생성하면 200 응답")
    void createNote_leader_success() throws Exception {
        CreateDecisionNoteRequest request = new CreateDecisionNoteRequest();
        request.setTitle("삼성전자 매수 근거");
        request.setContent("반도체 업황 개선 기대");
        request.setNoteType("DECISION");

        mockMvc.perform(post(basePath() + "/notes")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("삼성전자 매수 근거"))
                .andExpect(jsonPath("$.noteType").value("DECISION"));
    }

    @Test
    @DisplayName("매니저가 노트를 수정하면 200 응답")
    void updateNote_leader_success() throws Exception {
        // 노트 생성
        CreateDecisionNoteRequest createReq = new CreateDecisionNoteRequest();
        createReq.setTitle("원래 제목");
        createReq.setContent("원래 내용");
        createReq.setNoteType("REPORT");

        MvcResult createResult = mockMvc.perform(post(basePath() + "/notes")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        Long noteId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // 수정
        UpdateDecisionNoteRequest updateReq = new UpdateDecisionNoteRequest();
        updateReq.setTitle("수정된 제목");
        updateReq.setContent("수정된 내용");

        mockMvc.perform(patch(basePath() + "/notes/{noteId}", noteId)
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.content").value("수정된 내용"));
    }

    @Test
    @DisplayName("매니저가 노트를 삭제하면 204 응답")
    void deleteNote_leader_success() throws Exception {
        // 노트 생성
        CreateDecisionNoteRequest createReq = new CreateDecisionNoteRequest();
        createReq.setTitle("삭제할 노트");
        createReq.setContent("삭제 대상 내용");
        createReq.setNoteType("DECISION");

        MvcResult createResult = mockMvc.perform(post(basePath() + "/notes")
                        .header("Authorization", h.authHeader(leaderUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        Long noteId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // 삭제
        mockMvc.perform(delete(basePath() + "/notes/{noteId}", noteId)
                        .header("Authorization", h.authHeader(leaderUser)))
                .andExpect(status().isNoContent());
    }
}
