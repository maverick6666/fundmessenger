package com.fundmessenger.fund.service;

import com.fundmessenger.chat.service.ChatMessageService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.fund.dto.ApproveRequest;
import com.fundmessenger.fund.dto.CreateBuyRequest;
import com.fundmessenger.fund.dto.CreateSellRequest;
import com.fundmessenger.fund.entity.*;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.fund.repository.TradeRequestRepository;
import com.fundmessenger.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeRequestService 단위 테스트")
class TradeRequestServiceTest {

    @Mock
    private TradeRequestRepository tradeRequestRepository;

    @Mock
    private PositionService positionService;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private MemberTagRepository memberTagRepository;

    @InjectMocks
    private TradeRequestService tradeRequestService;

    private Club club;
    private Team fundTeam;
    private ClubMembership requester;
    private ClubMembership approver;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "투자동아리");
        fundTeam = TestFixtures.fundTeam(10L, club, "펀드A팀");

        var user1 = TestFixtures.user(1L, "requester@test.dev");
        requester = TestFixtures.approvedMembership(1L, user1, club);

        var user2 = TestFixtures.user(2L, "approver@test.dev");
        approver = TestFixtures.approvedMembership(2L, user2, club);
    }

    // ========== createBuyRequest ==========

    @Nested
    @DisplayName("createBuyRequest — 매수 요청 생성")
    class CreateBuyRequestTests {

        @Test
        @DisplayName("유효한 매수 요청을 생성한다")
        void createBuyRequest_success() {
            // given
            CreateBuyRequest dto = new CreateBuyRequest();
            dto.setTargetTicker("005930");
            dto.setTargetMarket("KRX");
            dto.setTargetTickerName("삼성전자");
            dto.setOrderQuantity(new BigDecimal("10"));
            dto.setBuyPrice(new BigDecimal("70000"));
            dto.setMemo("실적 호조 기대");

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(tradeRequestRepository.save(any(TradeRequest.class)))
                    .willAnswer(inv -> {
                        TradeRequest r = inv.getArgument(0);
                        r.setId(1L);
                        return r;
                    });

            // when
            TradeRequest result = tradeRequestService.createBuyRequest(10L, dto, requester);

            // then
            assertThat(result.getRequestType()).isEqualTo(RequestType.BUY);
            assertThat(result.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(result.getTargetTicker()).isEqualTo("005930");
            assertThat(result.getOrderQuantity()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(result.getRequester()).isEqualTo(requester);
            verify(tradeRequestRepository).save(any(TradeRequest.class));
        }
    }

    // ========== createSellRequest ==========

    @Nested
    @DisplayName("createSellRequest — 매도 요청 생성")
    class CreateSellRequestTests {

        @Test
        @DisplayName("유효한 매도 요청을 생성한다")
        void createSellRequest_success() {
            // given
            Position openPosition = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .ticker("005930")
                    .market("KRX")
                    .tickerName("삼성전자")
                    .status(PositionStatus.OPEN)
                    .totalQuantity(new BigDecimal("10"))
                    .build();

            CreateSellRequest dto = new CreateSellRequest();
            dto.setPositionId(100L);
            dto.setSellQuantity(new BigDecimal("5"));
            dto.setSellPrice(new BigDecimal("80000"));
            dto.setMemo("목표가 도달");

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionService.getPositionById(100L)).willReturn(openPosition);
            given(tradeRequestRepository.save(any(TradeRequest.class)))
                    .willAnswer(inv -> {
                        TradeRequest r = inv.getArgument(0);
                        r.setId(2L);
                        return r;
                    });

            // when
            TradeRequest result = tradeRequestService.createSellRequest(10L, dto, requester);

            // then
            assertThat(result.getRequestType()).isEqualTo(RequestType.SELL);
            assertThat(result.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(result.getSellQuantity()).isEqualByComparingTo(new BigDecimal("5"));
            assertThat(result.getPosition()).isEqualTo(openPosition);
        }

        @Test
        @DisplayName("매도 수량이 보유 수량을 초과하면 BadRequestException을 던진다")
        void createSellRequest_exceedsHolding_throws() {
            // given
            Position openPosition = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .status(PositionStatus.OPEN)
                    .totalQuantity(new BigDecimal("5"))
                    .build();

            CreateSellRequest dto = new CreateSellRequest();
            dto.setPositionId(100L);
            dto.setSellQuantity(new BigDecimal("10")); // 보유량 5 초과
            dto.setMemo("테스트");

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionService.getPositionById(100L)).willReturn(openPosition);

            // when & then
            assertThatThrownBy(() -> tradeRequestService.createSellRequest(10L, dto, requester))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("매도 수량")
                    .hasMessageContaining("초과");
        }

        @Test
        @DisplayName("CLOSED 포지션에 대한 매도 요청은 BadRequestException을 던진다")
        void createSellRequest_closedPosition_throws() {
            // given
            Position closedPosition = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .status(PositionStatus.CLOSED)
                    .build();

            CreateSellRequest dto = new CreateSellRequest();
            dto.setPositionId(100L);
            dto.setSellQuantity(new BigDecimal("1"));
            dto.setMemo("테스트");

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionService.getPositionById(100L)).willReturn(closedPosition);

            // when & then
            assertThatThrownBy(() -> tradeRequestService.createSellRequest(10L, dto, requester))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("OPEN 상태");
        }

        @Test
        @DisplayName("다른 팀의 포지션으로 매도 요청하면 ForbiddenException을 던진다")
        void createSellRequest_differentTeam_throws() {
            // given
            Team otherTeam = TestFixtures.fundTeam(20L, club, "다른팀");
            Position otherTeamPosition = Position.builder()
                    .id(100L)
                    .team(otherTeam)
                    .status(PositionStatus.OPEN)
                    .totalQuantity(new BigDecimal("10"))
                    .build();

            CreateSellRequest dto = new CreateSellRequest();
            dto.setPositionId(100L);
            dto.setSellQuantity(new BigDecimal("5"));
            dto.setMemo("테스트");

            given(teamRepository.findById(10L)).willReturn(Optional.of(fundTeam));
            given(positionService.getPositionById(100L)).willReturn(otherTeamPosition);

            // when & then
            assertThatThrownBy(() -> tradeRequestService.createSellRequest(10L, dto, requester))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ========== approveRequest ==========

    @Nested
    @DisplayName("approveRequest — 매매 요청 승인")
    class ApproveRequestTests {

        @Test
        @DisplayName("매수 요청을 승인하면 신규 포지션이 생성된다")
        void approveBuyRequest_createsPosition() {
            // given
            TradeRequest buyRequest = TradeRequest.builder()
                    .id(1L)
                    .team(fundTeam)
                    .requester(requester)
                    .requestType(RequestType.BUY)
                    .status(RequestStatus.PENDING)
                    .targetTicker("005930")
                    .targetMarket("KRX")
                    .targetTickerName("삼성전자")
                    .buyPrice(new BigDecimal("70000"))
                    .orderQuantity(new BigDecimal("10"))
                    .memo("매수")
                    .build();

            Position newPosition = Position.builder()
                    .id(100L).ticker("005930").status(PositionStatus.OPEN).build();

            given(tradeRequestRepository.findById(1L)).willReturn(Optional.of(buyRequest));
            given(positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                    10L, "005930", "KRX", PositionStatus.OPEN))
                    .willReturn(Optional.empty());
            given(positionService.createPositionFromRequest(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(newPosition);
            given(tradeRequestRepository.save(any(TradeRequest.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ApproveRequest approveData = new ApproveRequest();

            // when
            TradeRequest result = tradeRequestService.approveRequest(1L, approveData, approver);

            // then
            assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(result.getApprover()).isEqualTo(approver);
            assertThat(result.getApprovedAt()).isNotNull();
            assertThat(result.getPosition()).isEqualTo(newPosition);
        }

        @Test
        @DisplayName("매도 요청을 승인하면 포지션이 부분 매도된다")
        void approveSellRequest_reducesPosition() {
            // given
            Position openPosition = Position.builder()
                    .id(100L)
                    .team(fundTeam)
                    .ticker("005930")
                    .status(PositionStatus.OPEN)
                    .totalQuantity(new BigDecimal("10"))
                    .build();

            TradeRequest sellRequest = TradeRequest.builder()
                    .id(2L)
                    .team(fundTeam)
                    .requester(requester)
                    .requestType(RequestType.SELL)
                    .status(RequestStatus.PENDING)
                    .position(openPosition)
                    .sellPrice(new BigDecimal("80000"))
                    .sellQuantity(new BigDecimal("5"))
                    .memo("매도")
                    .build();

            given(tradeRequestRepository.findById(2L)).willReturn(Optional.of(sellRequest));
            given(positionRepository.findByIdForUpdate(100L)).willReturn(Optional.of(openPosition));
            given(tradeRequestRepository.save(any(TradeRequest.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ApproveRequest approveData = new ApproveRequest();

            // when
            TradeRequest result = tradeRequestService.approveRequest(2L, approveData, approver);

            // then
            assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
            // 부분 매도이므로 reducePosition이 호출되어야 함 (remaining = 10-5 = 5 > 0)
            verify(positionService).reducePosition(100L, new BigDecimal("80000"), new BigDecimal("5"));
        }

        @Test
        @DisplayName("이미 승인된 요청을 재승인하면 BadRequestException을 던진다")
        void approveRequest_alreadyApproved_throws() {
            // given
            TradeRequest approved = TradeRequest.builder()
                    .id(1L).status(RequestStatus.APPROVED).build();

            given(tradeRequestRepository.findById(1L)).willReturn(Optional.of(approved));

            // when & then
            assertThatThrownBy(() -> tradeRequestService.approveRequest(1L, new ApproveRequest(), approver))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PENDING 또는 DISCUSSION");
        }
    }
}
