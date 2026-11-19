package com.fundmessenger.jongtobang.service;

import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.jongtobang.dto.*;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class JongtobangServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockNewsCouplingRepository couplingRepository;
    @Mock private NewsCommentRepository commentRepository;
    @Mock private StockThreadRepository threadRepository;
    @Mock private ThreadReplyRepository replyRepository;
    @Mock private ThreadLikeRepository likeRepository;
    @Mock private NewsLikeRepository newsLikeRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private StockPriceService stockPriceService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private JongtobangService jongtobangService;

    private Club club;
    private ClubMembership member;
    private Stock stock;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user = TestFixtures.user(10L, "member@test.dev");
        member = TestFixtures.approvedMembership(100L, user, club);

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
    @DisplayName("스레드 작성 성공 — 종목에 스레드를 생성하면 저장된다")
    void createThread_success() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member);
        given(stockRepository.findById(1L)).willReturn(Optional.of(stock));

        CreateThreadRequest request = new CreateThreadRequest();
        request.setTitle("삼성전자 분석");
        request.setContent("실적이 좋아보입니다");

        StockThread saved = StockThread.builder()
                .club(club)
                .stock(stock)
                .author(member)
                .stockCode("005930")
                .stockName("삼성전자")
                .market("KOSPI")
                .title("삼성전자 분석")
                .content("실적이 좋아보입니다")
                .build();
        saved.setId(1L);
        saved.setCreatedAt(LocalDateTime.now());

        given(threadRepository.save(any(StockThread.class))).willReturn(saved);

        // when
        StockThreadResponse response = jongtobangService.createThread(1L, 1L, request, 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("삼성전자 분석");
        then(threadRepository).should().save(any(StockThread.class));
    }

    @Test
    @DisplayName("답글 작성 성공 — 스레드에 답글을 달면 저장되고 replyCount 증가한다")
    void createReply_success() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member);

        StockThread thread = StockThread.builder()
                .club(club)
                .stock(stock)
                .author(member)
                .stockCode("005930")
                .stockName("삼성전자")
                .market("KOSPI")
                .title("삼성전자 분석")
                .content("내용")
                .build();
        thread.setId(1L);
        thread.setCreatedAt(LocalDateTime.now());

        given(threadRepository.findById(1L)).willReturn(Optional.of(thread));

        ThreadReply saved = ThreadReply.builder()
                .thread(thread)
                .author(member)
                .content("동의합니다")
                .build();
        saved.setId(1L);
        saved.setCreatedAt(LocalDateTime.now());

        given(replyRepository.save(any(ThreadReply.class))).willReturn(saved);

        // when
        ThreadReplyResponse response = jongtobangService.createReply(1L, 1L, "동의합니다", null, 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("동의합니다");
        then(threadRepository).should().incrementReplyCount(1L);
    }

    @Test
    @DisplayName("종목 목록 조회 — 주목순 정렬로 종목이 반환된다")
    void getStocks_returnsList() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member);
        given(stockRepository.findByAttention(any(PageRequest.class))).willReturn(List.of(stock));

        // batch count mocks
        given(couplingRepository.countByStockIds(anyList())).willReturn(java.util.Arrays.<Object[]>asList(new Object[]{1L, 5L}));
        given(threadRepository.countByStockIdsAndClubId(anyList(), eq(1L))).willReturn(java.util.Arrays.<Object[]>asList(new Object[]{1L, 3L}));

        // when
        List<StockResponse> result = jongtobangService.getStocks(1L, "attention", null, null, null, 0, 20, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockName()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("스레드 공감 토글 — 좋아요가 없으면 추가하고, 있으면 제거한다")
    void toggleLike_addsAndRemoves() {
        // given — 좋아요 없는 상태에서 추가
        given(authService.validateClubMember(10L, 1L)).willReturn(member);

        StockThread thread = StockThread.builder()
                .club(club)
                .stock(stock)
                .author(member)
                .stockCode("005930")
                .stockName("삼성전자")
                .market("KOSPI")
                .title("테스트")
                .content("내용")
                .build();
        thread.setId(1L);
        thread.setCreatedAt(LocalDateTime.now());

        given(threadRepository.findById(1L)).willReturn(Optional.of(thread));
        given(likeRepository.findByThreadIdAndMemberId(1L, 100L)).willReturn(Optional.empty());
        given(likeRepository.save(any(ThreadLike.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Map<String, Object> result = jongtobangService.toggleLike(1L, 1L, 10L);

        // then
        assertThat(result.get("liked")).isEqualTo(true);
        then(likeRepository).should().save(any(ThreadLike.class));
        then(threadRepository).should().incrementLikeCount(1L);
    }
}
