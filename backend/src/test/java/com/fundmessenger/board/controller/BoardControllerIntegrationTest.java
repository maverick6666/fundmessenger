package com.fundmessenger.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BoardControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;

    // Mock external services
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
    private Board everyoneBoard;
    private Board executiveBoard;
    private SemiCategory tag;
    private ClubMembership executiveMembership;
    private ClubMembership regularMembership;

    @BeforeEach
    void setUp() {
        executiveUser = h.createUser("executive@test.dev");
        regularUser = h.createUser("regular@test.dev");

        club = h.createClubWithSchool("TestClub", "TestSchool");

        executiveMembership = h.createApprovedMembership(executiveUser, club, "임원");
        h.createExecutiveTag(executiveMembership, club);

        regularMembership = h.createApprovedMembership(regularUser, club, "일반부원");
        h.createRegularTag(regularMembership, club);

        everyoneBoard = h.createEveryoneBoard(club);
        executiveBoard = h.createExecutiveBoard(club);

        tag = h.createSemiCategory(club, "분석");
    }

    // ========== 1. Sidebar ==========

    @Test
    @DisplayName("GET /api/clubs/{id}/boards/sidebar -> 200 + boards")
    void getSidebar_returns200WithBoards() throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}/boards/sidebar", club.getId())
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boards").isArray())
                .andExpect(jsonPath("$.boards.length()").value(2))
                .andExpect(jsonPath("$.tags").isArray());
    }

    // ========== 2. Create post (EVERYONE board, regular member) ==========

    @Test
    @DisplayName("POST /api/clubs/{id}/posts (EVERYONE, regular) -> 200")
    void createPost_everyoneBoard_regularMember_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "테스트 글",
                "content", "테스트 내용입니다",
                "boardIds", List.of(everyoneBoard.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("테스트 글"))
                .andExpect(jsonPath("$.content").value("테스트 내용입니다"));
    }

    // ========== 3. Create post (EXECUTIVE_ONLY, regular member) -> 403 ==========

    @Test
    @DisplayName("POST /api/clubs/{id}/posts (EXECUTIVE_ONLY, regular) -> 403")
    void createPost_executiveBoard_regularMember_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "공지글 시도",
                "content", "일반부원이 공지사항에 글쓰기",
                "boardIds", List.of(executiveBoard.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ========== 4. Get club posts (paginated) ==========

    @Test
    @DisplayName("GET /api/clubs/{id}/posts -> 200 + paginated")
    void getClubPosts_returns200Paginated() throws Exception {
        // Create a post first
        createTestPost(regularUser, everyoneBoard);

        mockMvc.perform(get("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ========== 5. Get club posts with tag filter ==========

    @Test
    @DisplayName("GET /api/clubs/{id}/posts?tagId={tagId} -> filtered results")
    void getClubPosts_withTagFilter_returnsFiltered() throws Exception {
        createTestPost(regularUser, everyoneBoard);

        SemiCategory otherTag = h.createSemiCategory(club, "기타");

        mockMvc.perform(get("/api/clubs/{clubId}/posts", club.getId())
                        .param("tagId", otherTag.getId().toString())
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ========== 6. Update post by author ==========

    @Test
    @DisplayName("PUT /api/clubs/{id}/posts/{pid} by author -> 200")
    void updatePost_byAuthor_returns200() throws Exception {
        Long postId = createTestPost(regularUser, everyoneBoard);

        Map<String, Object> updateBody = Map.of(
                "title", "수정된 제목",
                "content", "수정된 내용",
                "boardIds", List.of(everyoneBoard.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(put("/api/clubs/{clubId}/posts/{postId}", club.getId(), postId)
                        .header("Authorization", h.authHeader(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"));
    }

    // ========== 7. Delete post ==========

    @Test
    @DisplayName("DELETE /api/clubs/{id}/posts/{pid} -> 204")
    void deletePost_byAuthor_returns204() throws Exception {
        Long postId = createTestPost(regularUser, everyoneBoard);

        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}", club.getId(), postId)
                        .header("Authorization", h.authHeader(regularUser)))
                .andExpect(status().isNoContent());
    }

    // ========== 8. All endpoints without JWT -> 401 ==========

    @Test
    @DisplayName("All board endpoints without JWT -> 401 (unauthenticated)")
    void allEndpoints_withoutJwt_returnsUnauthorizedOrForbidden() throws Exception {
        // SecurityConfig: custom AuthenticationEntryPoint returns 401 for unauthenticated
        mockMvc.perform(get("/api/clubs/{clubId}/boards/sidebar", club.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/clubs/{clubId}/posts", club.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ========== Helpers ==========

    private Long createTestPost(User user, Board board) throws Exception {
        Map<String, Object> body = Map.of(
                "title", "테스트 글",
                "content", "테스트 내용",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        String response = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
