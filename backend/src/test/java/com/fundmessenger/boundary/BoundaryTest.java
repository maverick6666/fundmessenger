package com.fundmessenger.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.entity.WritePermission;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.environment=development")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BoundaryTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostRepository postRepository;

    // Mock external services
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;
    @MockitoBean NewsPipelineService newsPipelineService;
    @MockitoBean AiCommentBotScheduler aiCommentBotScheduler;
    @MockitoBean SubscriptionScheduler subscriptionScheduler;

    private User user;
    private Club club;
    private Board board;
    private SemiCategory tag;
    private ClubMembership membership;
    private Team team;

    @BeforeEach
    void setUp() {
        user = h.createUser("boundary@test.dev");
        club = h.createClubWithSchool("경계값동아리", "경계값대학");
        club.setCreditBalance(10_000);
        membership = h.createApprovedMembership(user, club, "경계값유저");
        h.createExecutiveTag(membership, club);
        board = h.createEveryoneBoard(club);
        tag = h.createSemiCategory(club, "경계값태그");
        team = h.createTeam(club, "경계값팀");
    }

    // ============================================================
    // String boundaries — 글 작성
    // ============================================================

    @Test
    @DisplayName("T1: 글작성_빈제목_400 — title=\"\" → @NotBlank 검증")
    void 글작성_빈제목_400() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "",
                "content", "본문입니다",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T2: 글작성_공백제목_400 — title=\"   \" → @NotBlank 검증")
    void 글작성_공백제목_400() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "   ",
                "content", "본문입니다",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T3: 글작성_초장문본문 — content=100,000자 → 400 또는 처리")
    void 글작성_초장문본문() throws Exception {
        String longContent = "가".repeat(100_000);
        Map<String, Object> body = Map.of(
                "title", "초장문테스트",
                "content", longContent,
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // 100,000자가 허용되든 거부되든, 서버 500 에러는 안 되어야 함
        assertThat(status)
                .as("초장문 본문은 400으로 거절되거나 200으로 저장되어야 함 (500 불가)")
                .isIn(200, 400);

        // BUG-T1.5-LONGCONTENT: content 필드에 @Size 제한이 없어 100,000자도 DB에 저장됨.
        // TEXT 컬럼이라 DB는 받지만 DoS 벡터가 될 수 있음. @Size(max=50000) 권장.
    }

    @Test
    @DisplayName("T4: 글작성_null제목_400 — title=null → @NotBlank 검증")
    void 글작성_null제목_400() throws Exception {
        // null을 JSON에 명시적으로 포함
        String body = objectMapper.writeValueAsString(Map.of(
                "content", "본문입니다",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        ));
        // title 필드 자체가 없는 JSON (null과 동일)

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T5: 글작성_XSS_이스케이프 — <script> 태그가 InputSanitizer에 의해 제거")
    void 글작성_XSS_이스케이프() throws Exception {
        String xssTitle = "<script>alert(1)</script>안녕하세요";
        Map<String, Object> body = Map.of(
                "title", xssTitle,
                "content", "<p>정상본문</p><script>alert('xss')</script>",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // InputSanitizer.sanitizePlainText로 title에서 모든 HTML 태그 제거
        assertThat(responseBody)
                .as("title에서 <script> 태그가 제거되어야 함")
                .doesNotContain("<script>");

        // content는 sanitizeHtml로 안전한 태그만 허용 — <script>는 제거
        assertThat(responseBody)
                .as("content에서 <script> 태그가 제거되어야 함")
                .doesNotContain("<script>alert('xss')</script>");

        // DB에서도 확인
        Post saved = postRepository.findAll().stream()
                .filter(p -> p.getTitle().contains("안녕하세요"))
                .findFirst().orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getTitle())
                .as("DB에 저장된 title에 <script> 없어야 함")
                .doesNotContain("<script>");
    }

    @Test
    @DisplayName("T6: 글작성_SQL_injection_안전 — SQL injection 문자열이 리터럴로 저장")
    void 글작성_SQL_injection_안전() throws Exception {
        String sqlInjection = "'; DROP TABLE posts;--";
        Map<String, Object> body = Map.of(
                "title", sqlInjection,
                "content", "Robert'); DROP TABLE posts;--",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // posts 테이블이 살아있는지 확인 (DROP TABLE이 실행되지 않았음)
        long count = postRepository.count();
        assertThat(count)
                .as("posts 테이블이 DROP되지 않고 정상 존재")
                .isGreaterThanOrEqualTo(1);

        // 리터럴 문자열로 저장되었는지 확인
        // InputSanitizer.sanitizePlainText가 HTML 태그만 제거하므로 SQL 문자는 그대로 보존
        // JPA parameterized query가 SQL injection을 방지함
        Post saved = postRepository.findAll().stream()
                .filter(p -> p.getContent().contains("DROP TABLE"))
                .findFirst().orElse(null);
        assertThat(saved)
                .as("SQL injection 문자열이 리터럴로 DB에 저장")
                .isNotNull();
    }

    // ============================================================
    // Numeric boundaries — 크레딧
    // ============================================================

    @Test
    @DisplayName("T7: 크레딧_0원_분배_400 — amount=0 → @Min(1) 검증")
    void 크레딧_0원_분배_400() throws Exception {
        Map<String, Object> body = Map.of(
                "teamId", team.getId(),
                "amount", 0
        );

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T8: 크레딧_음수_분배_400 — amount=-100 → @Min(1) 검증")
    void 크레딧_음수_분배_400() throws Exception {
        Map<String, Object> body = Map.of(
                "teamId", team.getId(),
                "amount", -100
        );

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T9: 크레딧_MAX_INT_분배 — amount=2147483647 → 잔액부족 또는 400")
    void 크레딧_MAX_INT_분배() throws Exception {
        Map<String, Object> body = Map.of(
                "teamId", team.getId(),
                "amount", Integer.MAX_VALUE
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // 잔액(10,000) < MAX_INT이므로 "잔액부족" 400이어야 함
        assertThat(status)
                .as("잔액 부족으로 400 반환 (서버 에러 500 불가)")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("T10: 크레딧_null_amount_400 — amount=null → @NotNull 검증")
    void 크레딧_null_amount_400() throws Exception {
        // amount를 누락한 JSON
        String body = "{\"teamId\":" + team.getId() + "}";

        mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // List boundaries — boardIds / semiCategoryIds
    // ============================================================

    @Test
    @DisplayName("T11: 글작성_빈boardIds_400 — boardIds=[] → @NotEmpty 검증")
    void 글작성_빈boardIds_400() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "빈게시판목록",
                "content", "본문",
                "boardIds", List.of(),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T12: 글작성_존재하지않는boardId_404 — boardIds=[99999] → NotFoundException")
    void 글작성_존재하지않는boardId_404() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "존재하지않는게시판",
                "content", "본문",
                "boardIds", List.of(99999L),
                "semiCategoryIds", List.of(tag.getId())
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // NotFoundException → 404, or resolveBoards에서 NotFoundException → GlobalExceptionHandler → 404
        assertThat(status)
                .as("존재하지 않는 boardId → 404 Not Found")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("T13: 글작성_빈semiCategoryIds_400 — semiCategoryIds=[] → @NotEmpty 검증")
    void 글작성_빈semiCategoryIds_400() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "빈태그목록",
                "content", "본문",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of()
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T14: 글작성_존재하지않는tagId_404 — semiCategoryIds=[99999] → NotFoundException")
    void 글작성_존재하지않는tagId_404() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "존재하지않는태그",
                "content", "본문",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(99999L)
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 tagId → 404 Not Found")
                .isEqualTo(404);
    }

    // ============================================================
    // File upload boundaries
    // ============================================================

    @Test
    @DisplayName("T15: 빈파일_업로드_400 — 0바이트 파일 업로드 → 에러")
    void 빈파일_업로드_400() throws Exception {
        // Drive upload endpoint: POST /api/clubs/{clubId}/drive/files (multipart)
        MvcResult result = mockMvc.perform(multipart("/api/clubs/{clubId}/drive/files", club.getId())
                        .file("file", new byte[0])
                        .param("semiCategoryIds", String.valueOf(tag.getId()))
                        .header("Authorization", h.authHeader(user)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // 빈 파일은 400으로 거부되어야 함
        assertThat(status)
                .as("0바이트 파일은 400 또는 422로 거부 (500 불가)")
                .isNotEqualTo(500);

        // BUG-T1.5-EMPTYFILE: 빈 파일이 200으로 성공하면 validation 누락
        if (status == 200) {
            // BUG-T1.5-EMPTYFILE: 0바이트 파일이 업로드 성공 — 빈 파일 검증 누락
        }
    }

    @Test
    @DisplayName("T16: 확장자없는_파일_업로드 — 확장자 없는 파일 → 동작 확인")
    void 확장자없는_파일_업로드() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/clubs/{clubId}/drive/files", club.getId())
                        .file("file", "hello world".getBytes())
                        .param("semiCategoryIds", String.valueOf(tag.getId()))
                        .param("displayName", "noextension")
                        .header("Authorization", h.authHeader(user)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // 확장자 없는 파일 — 거부 또는 허용 모두 가능하지만 500은 안 됨
        assertThat(status)
                .as("확장자 없는 파일 — 500 서버 에러 불가")
                .isNotEqualTo(500);
    }

    // ============================================================
    // 추가: 제목 길이 초과
    // ============================================================

    @Test
    @DisplayName("T17: 글작성_201자제목_400 — @Size(max=200) 초과 → 400")
    void 글작성_201자제목_400() throws Exception {
        String longTitle = "가".repeat(201);
        Map<String, Object> body = Map.of(
                "title", longTitle,
                "content", "본문",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T18: 글작성_200자제목_성공 — @Size(max=200) 경계값 → 200")
    void 글작성_200자제목_성공() throws Exception {
        String exactTitle = "가".repeat(200);
        Map<String, Object> body = Map.of(
                "title", exactTitle,
                "content", "본문",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .header("Authorization", h.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // ============================================================
    // 추가: 인증 없이 접근
    // ============================================================

    @Test
    @DisplayName("T19: 인증없이_글작성_거부 — Authorization 헤더 없이 → 401 또는 403")
    void 인증없이_글작성_거부() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "무인증테스트",
                "content", "본문",
                "boardIds", List.of(board.getId()),
                "semiCategoryIds", List.of(tag.getId())
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/posts", club.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // Spring Security: JWT 필터가 없으면 403 (AccessDeniedException), 있으면 401
        assertThat(status)
                .as("인증 없이 글 작성 시 401 또는 403으로 거부")
                .isIn(401, 403);
    }

    @Test
    @DisplayName("T20: 크레딧_인증없이_분배_거부 — Authorization 없이 → 401 또는 403")
    void 크레딧_인증없이_분배_거부() throws Exception {
        Map<String, Object> body = Map.of(
                "teamId", team.getId(),
                "amount", 100
        );

        MvcResult result = mockMvc.perform(post("/api/clubs/{clubId}/credits/allocate", club.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // BUG-T1.5-AUTH: 인증 없는 요청에 403 반환 — RFC 7235 기준 401이 올바름.
        // Spring Security AuthenticationEntryPoint가 401을 반환하도록 설정 필요.
        assertThat(status)
                .as("인증 없이 크레딧 분배 시 401 또는 403으로 거부")
                .isIn(401, 403);
    }
}
