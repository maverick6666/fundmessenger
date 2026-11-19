package com.fundmessenger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.PostRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IDOR (Insecure Direct Object Reference) 보안 테스트.
 *
 * 크로스 클럽 접근, 권한 우회, 타인 자원 조작을 검증한다.
 * 모든 테스트는 4xx (403/400/404) 를 기대 — 200이 반환되면 보안 버그.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdorSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostRepository postRepository;

    // ── External service mocks ──
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;
    @MockitoBean NewsPipelineService newsPipelineService;
    @MockitoBean AiCommentBotScheduler aiCommentBotScheduler;
    @MockitoBean SubscriptionScheduler subscriptionScheduler;

    // ── Club A: user1(임원), user2(일반) ──
    private User user1, user2;
    private Club clubA;
    private Board boardA;
    private SemiCategory tagA;
    private ClubMembership membership1, membership2;
    private Post postByUser1;

    // ── Club B: user3(임원) ──
    private User user3;
    private Club clubB;
    private Board boardB;
    private SemiCategory tagB;
    private ClubMembership membership3;
    private Team teamB;

    // ── No club: user4 ──
    private User user4;

    // ── Super admin ──
    private User superAdmin;

    @BeforeEach
    void setUp() {
        // ---- Club A ----
        user1 = h.createUser("idor-exec-a@test.dev");
        user2 = h.createUser("idor-reg-a@test.dev");
        clubA = h.createClubWithSchool("ClubA-IDOR", "SchoolA-IDOR");
        membership1 = h.createApprovedMembership(user1, clubA, "홍길동A");
        membership2 = h.createApprovedMembership(user2, clubA, "김철수A");
        h.createExecutiveTag(membership1, clubA);
        h.createRegularTag(membership2, clubA);
        boardA = h.createEveryoneBoard(clubA);
        tagA = h.createSemiCategory(clubA, "자유태그A");

        // Create a post by user1 in Club A
        postByUser1 = Post.builder()
                .author(membership1)
                .title("User1의 글")
                .content("테스트 내용")
                .build();
        postByUser1.getBoards().add(boardA);
        postByUser1.getSemiCategories().add(tagA);
        postByUser1 = postRepository.save(postByUser1);

        // ---- Club B ----
        user3 = h.createUser("idor-exec-b@test.dev");
        clubB = h.createClubWithSchool("ClubB-IDOR", "SchoolB-IDOR");
        membership3 = h.createApprovedMembership(user3, clubB, "이영희B");
        h.createExecutiveTag(membership3, clubB);
        boardB = h.createEveryoneBoard(clubB);
        tagB = h.createSemiCategory(clubB, "자유태그B");
        teamB = h.createTeam(clubB, "TeamB");

        // ---- No club user ----
        user4 = h.createUser("idor-noclu@test.dev");

        // ---- Super admin ----
        superAdmin = h.createSuperAdmin();
    }

    // ================================================================
    // T1.1: Cross-club 글 작성 거부
    // ================================================================
    @Test
    @DisplayName("T1.1: user1(ClubA 임원) -> ClubB 게시판에 글 작성 시도 -> 403/4xx")
    void crossClub_글작성_거부() throws Exception {
        var body = Map.of(
                "title", "불법 글",
                "content", "크로스클럽 테스트",
                "boardIds", List.of(boardB.getId()),
                "semiCategoryIds", List.of(tagB.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", clubB.getId())
                        .header("Authorization", h.authHeader(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.1: If 200, cross-club post creation is not validated
    }

    // ================================================================
    // T1.2: Cross-club 크레딧 분배 거부
    // ================================================================
    @Test
    @DisplayName("T1.2: user1(ClubA 임원) -> ClubB에 크레딧 분배 시도 -> 403/4xx")
    void crossClub_크레딧분배_거부() throws Exception {
        var body = Map.of(
                "teamId", teamB.getId(),
                "amount", 100,
                "description", "크로스클럽 분배 시도"
        );

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", clubB.getId())
                        .header("Authorization", h.authHeader(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.2: If 200, cross-club credit allocation is not validated
    }

    // ================================================================
    // T1.3: Cross-club 멤버 승인 거부
    // ================================================================
    @Test
    @DisplayName("T1.3: user1(ClubA 임원) -> ClubB의 가입 요청 승인 시도 -> 403/4xx")
    void crossClub_멤버승인_거부() throws Exception {
        // Create a pending membership in Club B
        User pendingUser = h.createUser("idor-pending@test.dev");
        ClubMembership pendingMembership = h.createPendingMembership(pendingUser, clubB, "가입대기자");

        mockMvc.perform(put("/api/clubs/{clubId}/admin/join-requests/{membershipId}/approve",
                        clubB.getId(), pendingMembership.getId())
                        .header("Authorization", h.authHeader(user1)))
                .andExpect(status().isForbidden());
        // BUG-T1.3: If 200, cross-club member approval is not validated
    }

    // ================================================================
    // T1.4: Cross-club 팀 생성 거부
    // ================================================================
    @Test
    @DisplayName("T1.4: user1(ClubA 임원) -> ClubB에 팀 생성 시도 -> 403/4xx")
    void crossClub_팀생성_거부() throws Exception {
        var body = Map.of(
                "name", "불법팀"
        );

        mockMvc.perform(post("/api/clubs/{clubId}/admin/teams", clubB.getId())
                        .header("Authorization", h.authHeader(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.4: If 200, cross-club team creation is not validated
    }

    // ================================================================
    // T1.5: 일반부원 -> 관리자 API 거부
    // ================================================================
    @Test
    @DisplayName("T1.5: user2(ClubA 일반부원) -> ClubA 관리자 팀 생성 API -> 403")
    void regularMember_관리자API_거부() throws Exception {
        var body = Map.of(
                "name", "권한우회팀"
        );

        mockMvc.perform(post("/api/clubs/{clubId}/admin/teams", clubA.getId())
                        .header("Authorization", h.authHeader(user2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.5: If 200, regular member can access admin API
    }

    // ================================================================
    // T1.6: 미소속 유저 -> 동아리 접근 거부
    // ================================================================
    @Test
    @DisplayName("T1.6: user4(미소속) -> ClubA 사이드바 접근 -> 403/4xx")
    void nonMember_동아리접근_거부() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/boards/sidebar", clubA.getId())
                        .header("Authorization", h.authHeader(user4)))
                .andExpect(status().isForbidden());
        // BUG-T1.6: If 200, non-member can access club data
    }

    // ================================================================
    // T1.7: 타인 글 수정 거부
    // ================================================================
    @Test
    @DisplayName("T1.7: user2(일반부원) -> user1의 글 수정 시도 -> 403")
    void 타인글수정_거부() throws Exception {
        var body = Map.of(
                "title", "수정된 제목",
                "content", "수정된 내용",
                "boardIds", List.of(boardA.getId()),
                "semiCategoryIds", List.of(tagA.getId())
        );

        mockMvc.perform(put("/api/clubs/{clubId}/posts/{postId}",
                        clubA.getId(), postByUser1.getId())
                        .header("Authorization", h.authHeader(user2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.7: If 200, any member can edit other's posts
    }

    // ================================================================
    // T1.8: 타인 글 삭제 거부
    // ================================================================
    @Test
    @DisplayName("T1.8: user2(일반부원) -> user1의 글 삭제 시도 -> 403")
    void 타인글삭제_거부() throws Exception {
        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}",
                        clubA.getId(), postByUser1.getId())
                        .header("Authorization", h.authHeader(user2)))
                .andExpect(status().isForbidden());
        // BUG-T1.8: If 200, any member can delete other's posts
    }

    // ================================================================
    // T1.9: 일반 유저 -> SuperAdmin 전용 API 거부
    // ================================================================
    @Test
    @DisplayName("T1.9: user1(일반 유저) -> /api/admin/dashboard 접근 -> 403")
    void superAdmin전용_일반유저_거부() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", h.authHeader(user1)))
                .andExpect(status().isForbidden());
        // BUG-T1.9: If 200, non-superadmin can access admin dashboard
    }

    // ================================================================
    // Bonus: Cross-club 글 조회 거부 (user1 -> ClubB의 사이드바)
    // ================================================================
    @Test
    @DisplayName("T1.10: user1(ClubA) -> ClubB 사이드바 조회 -> 403/4xx")
    void crossClub_사이드바조회_거부() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/boards/sidebar", clubB.getId())
                        .header("Authorization", h.authHeader(user1)))
                .andExpect(status().isForbidden());
        // BUG-T1.10: If 200, user can browse other club's board structure
    }

    // ================================================================
    // Bonus: Cross-club 멤버 목록 조회 거부
    // ================================================================
    @Test
    @DisplayName("T1.11: user1(ClubA) -> ClubB 멤버 목록 조회 -> 403/4xx")
    void crossClub_멤버목록조회_거부() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/members", clubB.getId())
                        .header("Authorization", h.authHeader(user1)))
                .andExpect(status().isForbidden());
        // BUG-T1.11: If 200, user can see other club's member list
    }

    // ================================================================
    // Bonus: Cross-club 글 상세 조회 거부 (user3 -> ClubA의 글)
    // ================================================================
    @Test
    @DisplayName("T1.12: user3(ClubB) -> ClubA의 글 상세 조회 -> 403/4xx")
    void crossClub_글상세조회_거부() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/posts/{postId}",
                        clubA.getId(), postByUser1.getId())
                        .header("Authorization", h.authHeader(user3)))
                .andExpect(status().isForbidden());
        // BUG-T1.12: If 200, cross-club post detail is exposed
    }

    // ================================================================
    // Bonus: 미인증 유저 -> 401
    // ================================================================
    @Test
    @DisplayName("T1.13: JWT 없이 접근 -> 401 or 403")
    void 미인증_접근_거부() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/boards/sidebar", clubA.getId()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Spring Security may return 401 or 403 for unauthenticated requests
                    assertThat(status == 401 || status == 403)
                            .as("Expected 401 or 403 but got %d", status)
                            .isTrue();
                });
    }

    // ================================================================
    // Bonus: Cross-club 관리자 게시판 생성 거부
    // ================================================================
    @Test
    @DisplayName("T1.14: user1(ClubA 임원) -> ClubB 관리자 게시판 생성 -> 403/4xx")
    void crossClub_관리자게시판생성_거부() throws Exception {
        var body = Map.of(
                "name", "불법게시판",
                "writePermission", "EVERYONE"
        );

        mockMvc.perform(post("/api/clubs/{clubId}/admin/boards", clubB.getId())
                        .header("Authorization", h.authHeader(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        // BUG-T1.14: If 200, cross-club admin board creation is not validated
    }
}
