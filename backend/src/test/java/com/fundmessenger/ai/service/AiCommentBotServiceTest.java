package com.fundmessenger.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.repository.GlobalSettingsRepository;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AiCommentBotServiceTest {

    @Mock private ClubSettingsRepository settingsRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMembershipRepository membershipRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockNewsCouplingRepository couplingRepository;
    @Mock private CanonicalNewsRepository canonicalNewsRepository;
    @Mock private NewsCommentRepository newsCommentRepository;
    @Mock private StockThreadRepository threadRepository;
    @Mock private ThreadReplyRepository threadReplyRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private CerebrasClient cerebrasClient;
    @Mock private GlobalSettingsRepository globalSettingsRepository;
    @Mock private BoardRepository boardRepository;

    @InjectMocks private AiCommentBotService aiCommentBotService;

    private Club club;
    private ClubMembership botMembership;
    private Stock stock;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");

        botMembership = ClubMembership.builder()
                .user(null)
                .club(club)
                .name("🐂 황소")
                .studentId("BOT-BULLISH")
                .phone("BOT")
                .status(MemberStatus.APPROVED)
                .bot(true)
                .build();
        botMembership.setId(999L);
        botMembership.setAppliedAt(LocalDateTime.now());
        botMembership.setApprovedAt(LocalDateTime.now());

        stock = Stock.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .market("KOSPI")
                .currentPrice(new BigDecimal("72000"))
                .changeRate(new BigDecimal("1.5"))
                .build();
        stock.setId(1L);
        stock.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("executeRound로 게시판 댓글 생성 — CerebrasClient를 호출하여 PostComment를 저장한다")
    void executeRound_boardComment_savesPostComment() throws Exception {
        // given
        ClubSettings settings = ClubSettings.builder()
                .aiCommentBotEnabled(true)
                .aiCommentBotScope("BOARD_ONLY")
                .build();
        settings.setId(1L);
        given(settingsRepository.findById(1L)).willReturn(Optional.of(settings));
        given(cerebrasClient.isAvailable()).willReturn(true);

        // 봇 멤버십 조회 — 기존 봇 반환
        given(membershipRepository.findByClubIdAndStatusOrderByNameAsc(1L, MemberStatus.APPROVED))
                .willReturn(List.of(botMembership));

        // pending 응답 없음
        given(postCommentRepository.findNeedsAiResponseByClubId(1L)).willReturn(List.of());
        given(threadReplyRepository.findByNeedsAiResponseTrueAndThread_Club_Id(1L)).willReturn(List.of());
        given(newsCommentRepository.findByNeedsAiResponseTrue()).willReturn(List.of());

        // 대상 게시글
        User author = TestFixtures.user(10L, "author@test.dev");
        ClubMembership authorMember = TestFixtures.approvedMembership(100L, author, club);
        Board everyoneBoard = TestFixtures.everyoneBoard(1L, club);

        Post targetPost = Post.builder()
                .boards(new HashSet<>(Set.of(everyoneBoard)))
                .author(authorMember)
                .title("삼성전자 분석")
                .content("실적이 좋아보입니다")
                .build();
        targetPost.setId(1L);
        targetPost.setCreatedAt(LocalDateTime.now());

        Page<Post> postPage = new PageImpl<>(List.of(targetPost));
        given(postRepository.findWithFilters(eq(1L), isNull(), isNull(), any(PageRequest.class)))
                .willReturn(postPage);

        // 같은 페르소나 댓글 없음
        given(postCommentRepository.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

        // Cerebras 응답
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree("{\"content\": \"좋은 분석이네요!\"}");
        given(cerebrasClient.generateJson(anyString(), anyString(), anyInt())).willReturn(jsonResponse);

        given(postCommentRepository.save(any(PostComment.class))).willAnswer(inv -> {
            PostComment c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        // when
        Map<String, Object> result = aiCommentBotService.executeRound(
                1L, AiCommentBotService.BotPersona.BULLISH, AiCommentBotService.ContentType.BOARD_COMMENT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("type")).isEqualTo("POST_COMMENT");
        then(postCommentRepository).should().save(any(PostComment.class));
    }

    @Test
    @DisplayName("봇 멤버십 조회 또는 생성 — 기존 봇이 없으면 새로 생성한다")
    void getOrCreateBotMembership_createsNew() {
        // given
        given(membershipRepository.findByClubIdAndStatusOrderByNameAsc(1L, MemberStatus.APPROVED))
                .willReturn(List.of());  // 기존 봇 없음
        given(clubRepository.findById(1L)).willReturn(Optional.of(club));

        given(membershipRepository.save(any(ClubMembership.class))).willAnswer(inv -> {
            ClubMembership m = inv.getArgument(0);
            m.setId(998L);
            return m;
        });

        // when
        ClubMembership result = aiCommentBotService.getOrCreateBotMembership(
                1L, AiCommentBotService.BotPersona.BEARISH);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isBot()).isTrue();
        assertThat(result.getStudentId()).isEqualTo("BOT-BEARISH");
        then(membershipRepository).should().save(any(ClubMembership.class));
    }
}
