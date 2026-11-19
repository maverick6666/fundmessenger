package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.fund.dto.ClosePositionRequest;
import com.fundmessenger.fund.dto.CreatePositionRequest;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PositionService 단위 테스트")
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private PositionService positionService;

    private Club club;
    private Team fundTeam;
    private ClubMembership opener;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "투자동아리");
        fundTeam = TestFixtures.fundTeam(10L, club, "펀드A팀");
        var user = TestFixtures.user(1L, "opener@test.dev");
        opener = TestFixtures.approvedMembership(1L, user, club);
    }

    // ========== createPosition ==========

    @Nested
    @DisplayName("createPosition — 포지션 직접 생성")
    class CreatePosition {

        @Test
        @DisplayName("유효한 요청으로 OPEN 포지션을 생성한다")
        void createPosition_success() {
            // given
            CreatePositionRequest request = new CreatePositionRequest();
            request.setTicker("005930");
            request.setTickerName("삼성전자");
            request.setMarket("KRX");
            request.setBuyPrice(new BigDecimal("70000"));
            request.setQuantity(new BigDecimal("10"));

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                    10L, "005930", "KRX", PositionStatus.OPEN))
                    .willReturn(Optional.empty());
            given(positionRepository.save(any(Position.class)))
                    .willAnswer(inv -> {
                        Position p = inv.getArgument(0);
                        p.setId(100L);
                        return p;
                    });

            // when
            Position result = positionService.createPosition(10L, request, opener);

            // then
            assertThat(result.getTicker()).isEqualTo("005930");
            assertThat(result.getStatus()).isEqualTo(PositionStatus.OPEN);
            assertThat(result.getAverageBuyPrice()).isEqualByComparingTo(new BigDecimal("70000"));
            assertThat(result.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(result.getTotalBuyAmount()).isEqualByComparingTo(new BigDecimal("700000"));
            assertThat(result.isInfoConfirmed()).isTrue();
        }

        @Test
        @DisplayName("동일 종목의 OPEN 포지션이 이미 존재하면 BadRequestException을 던진다")
        void createPosition_duplicateOpen_throws() {
            // given
            CreatePositionRequest request = new CreatePositionRequest();
            request.setTicker("005930");
            request.setMarket("KRX");
            request.setBuyPrice(new BigDecimal("70000"));
            request.setQuantity(new BigDecimal("10"));

            Position existingPosition = Position.builder()
                    .id(50L).ticker("005930").market("KRX").status(PositionStatus.OPEN).build();

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                    10L, "005930", "KRX", PositionStatus.OPEN))
                    .willReturn(Optional.of(existingPosition));

            // when & then
            assertThatThrownBy(() -> positionService.createPosition(10L, request, opener))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("이미 진행 중인 포지션");
        }

        @Test
        @DisplayName("FUND 타입이 아닌 팀에서는 BadRequestException을 던진다")
        void createPosition_nonFundTeam_throws() {
            // given
            Team customTeam = TestFixtures.team(20L, club, "일반팀");
            CreatePositionRequest request = new CreatePositionRequest();
            request.setTicker("005930");
            request.setMarket("KRX");
            request.setBuyPrice(BigDecimal.ONE);
            request.setQuantity(BigDecimal.ONE);

            given(teamRepository.findById(20L)).willReturn(Optional.of(customTeam));

            // when & then
            assertThatThrownBy(() -> positionService.createPosition(20L, request, opener))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("FUND 타입 팀에서만");
        }
    }

    // ========== closePosition ==========

    @Nested
    @DisplayName("closePosition — 포지션 종료 및 P&L 계산")
    class ClosePosition {

        @Test
        @DisplayName("매도가 80,000원으로 10주 종료 시 P&L이 정확히 계산된다")
        void closePosition_calculatesCorrectPnl() {
            // given
            Position openPosition = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .ticker("005930")
                    .market("KRX")
                    .status(PositionStatus.OPEN)
                    .averageBuyPrice(new BigDecimal("70000"))
                    .totalQuantity(new BigDecimal("10"))
                    .totalBuyAmount(new BigDecimal("700000"))
                    .realizedProfitLoss(BigDecimal.ZERO)
                    .openedAt(LocalDateTime.now().minusDays(5))
                    .build();

            given(positionRepository.findById(100L)).willReturn(Optional.of(openPosition));
            given(positionRepository.save(any(Position.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ClosePositionRequest closeReq = new ClosePositionRequest();
            closeReq.setSellPrice(new BigDecimal("80000"));
            closeReq.setSellQuantity(new BigDecimal("10"));

            // when
            Position closed = positionService.closePosition(100L, closeReq, opener);

            // then
            assertThat(closed.getStatus()).isEqualTo(PositionStatus.CLOSED);
            // P&L = (80000 * 10) - (70000 * 10) = 100000
            assertThat(closed.getRealizedProfitLoss()).isEqualByComparingTo(new BigDecimal("100000"));
            // profitRate = 100000 / 700000 = 0.1429 (4 decimals)
            assertThat(closed.getProfitRate()).isEqualByComparingTo(
                    new BigDecimal("100000").divide(new BigDecimal("700000"), 4, RoundingMode.HALF_UP));
            assertThat(closed.getClosedAt()).isNotNull();
            assertThat(closed.getHoldingPeriodHours()).isNotNull();
        }

        @Test
        @DisplayName("이미 종료된 포지션을 다시 종료하면 BadRequestException을 던진다")
        void closePosition_alreadyClosed_throws() {
            // given
            Position closedPosition = Position.builder()
                    .id(100L).status(PositionStatus.CLOSED).build();

            given(positionRepository.findById(100L)).willReturn(Optional.of(closedPosition));

            ClosePositionRequest closeReq = new ClosePositionRequest();
            closeReq.setSellPrice(BigDecimal.TEN);
            closeReq.setSellQuantity(BigDecimal.ONE);

            // when & then
            assertThatThrownBy(() -> positionService.closePosition(100L, closeReq, opener))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("이미 종료된 포지션");
        }

        @Test
        @DisplayName("부분 매도 후 종료 시 누적 실현손익이 반영된다")
        void closePosition_afterPartialSell_accumulatesPnl() {
            // given — 이전 부분 매도로 realizedProfitLoss=50000 누적, 잔량=5
            Position position = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .ticker("005930")
                    .market("KRX")
                    .status(PositionStatus.OPEN)
                    .averageBuyPrice(new BigDecimal("70000"))
                    .totalQuantity(new BigDecimal("5"))
                    .totalBuyAmount(new BigDecimal("700000"))
                    .realizedProfitLoss(new BigDecimal("50000"))
                    .openedAt(LocalDateTime.now().minusDays(3))
                    .build();

            given(positionRepository.findById(100L)).willReturn(Optional.of(position));
            given(positionRepository.save(any(Position.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ClosePositionRequest closeReq = new ClosePositionRequest();
            closeReq.setSellPrice(new BigDecimal("75000"));
            closeReq.setSellQuantity(new BigDecimal("5"));

            // when
            Position closed = positionService.closePosition(100L, closeReq, opener);

            // then — closingPnl = (75000*5) - (70000*5) = 25000, total = 50000+25000 = 75000
            assertThat(closed.getRealizedProfitLoss()).isEqualByComparingTo(new BigDecimal("75000"));
        }
    }

    // ========== getPositions ==========

    @Nested
    @DisplayName("getPositions — 포지션 목록 조회")
    class GetPositions {

        @Test
        @DisplayName("status 파라미터 없이 호출하면 전체 포지션을 반환한다")
        void getPositions_noFilter_returnsAll() {
            // given
            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionRepository.findByTeamIdOrderByCreatedAtDesc(10L))
                    .willReturn(List.of(Position.builder().id(1L).build()));

            // when
            List<Position> result = positionService.getPositions(10L, null);

            // then
            assertThat(result).hasSize(1);
            verify(positionRepository).findByTeamIdOrderByCreatedAtDesc(10L);
        }

        @Test
        @DisplayName("status=OPEN으로 필터링하면 OPEN 포지션만 반환한다")
        void getPositions_filterByOpen_returnsFiltered() {
            // given
            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionRepository.findByTeamIdAndStatusOrderByCreatedAtDesc(10L, PositionStatus.OPEN))
                    .willReturn(Collections.emptyList());

            // when
            List<Position> result = positionService.getPositions(10L, PositionStatus.OPEN);

            // then
            assertThat(result).isEmpty();
            verify(positionRepository).findByTeamIdAndStatusOrderByCreatedAtDesc(10L, PositionStatus.OPEN);
        }
    }

    // ========== BigDecimal 정밀도 ==========

    @Nested
    @DisplayName("BigDecimal 정밀도 검증")
    class BigDecimalPrecision {

        @Test
        @DisplayName("평균매수가 나눗셈 시 4자리 반올림이 적용된다")
        void averageBuyPrice_roundsTo4Decimals() {
            // given — addToPosition으로 추가매수 후 평균가 재계산
            Position position = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .status(PositionStatus.OPEN)
                    .averageBuyPrice(new BigDecimal("100"))
                    .totalQuantity(new BigDecimal("3"))
                    .totalBuyAmount(new BigDecimal("300"))
                    .build();

            given(positionRepository.findById(100L)).willReturn(Optional.of(position));
            given(positionRepository.save(any(Position.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when — 추가매수: 가격 200, 수량 3 → 총매수금 = 300+600=900, 총수량=6
            Position result = positionService.addToPosition(
                    100L,
                    new BigDecimal("200"),
                    new BigDecimal("3"),
                    null, null, null);

            // then — 평균가 = 900/6 = 150.0000
            BigDecimal expected = new BigDecimal("900").divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP);
            assertThat(result.getAverageBuyPrice()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("수익률 계산 시 4자리 반올림이 적용된다")
        void profitRate_roundsTo4Decimals() {
            // given — 매수금 300, 이익 100 → profitRate = 100/300 = 0.3333
            Position position = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .status(PositionStatus.OPEN)
                    .averageBuyPrice(new BigDecimal("100"))
                    .totalQuantity(new BigDecimal("3"))
                    .totalBuyAmount(new BigDecimal("300"))
                    .realizedProfitLoss(BigDecimal.ZERO)
                    .openedAt(LocalDateTime.now().minusDays(1))
                    .build();

            given(positionRepository.findById(100L)).willReturn(Optional.of(position));
            given(positionRepository.save(any(Position.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ClosePositionRequest closeReq = new ClosePositionRequest();
            closeReq.setSellPrice(new BigDecimal("133.3333"));
            closeReq.setSellQuantity(new BigDecimal("3"));

            // when
            Position closed = positionService.closePosition(100L, closeReq, opener);

            // then — profitRate은 4자리까지
            assertThat(closed.getProfitRate().scale()).isLessThanOrEqualTo(4);
        }
    }
}
