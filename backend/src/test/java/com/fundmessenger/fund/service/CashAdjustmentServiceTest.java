package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.fund.dto.CreateCashAdjustmentRequest;
import com.fundmessenger.fund.entity.AdjustmentType;
import com.fundmessenger.fund.entity.CashAdjustment;
import com.fundmessenger.fund.repository.CashAdjustmentRepository;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CashAdjustmentService 단위 테스트")
class CashAdjustmentServiceTest {

    @Mock
    private CashAdjustmentRepository adjustmentRepository;

    @Mock
    private PositionService positionService;

    @InjectMocks
    private CashAdjustmentService cashAdjustmentService;

    private Club club;
    private Team fundTeam;
    private ClubMembership recorder;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "투자동아리");
        fundTeam = TestFixtures.fundTeam(10L, club, "펀드A팀");

        var user = TestFixtures.user(1L, "recorder@test.dev");
        recorder = TestFixtures.approvedMembership(1L, user, club);
    }

    // ========== recordAdjustment ==========

    @Nested
    @DisplayName("recordAdjustment — 현금 조정 기록")
    class RecordAdjustment {

        @Test
        @DisplayName("입금(DEPOSIT) 조정을 성공적으로 기록한다")
        void recordDeposit_success() {
            // given
            CreateCashAdjustmentRequest request = new CreateCashAdjustmentRequest();
            request.setAdjustmentType("DEPOSIT");
            request.setCurrency("KRW");
            request.setAmount(new BigDecimal("1000000"));
            request.setMemo("초기 투자금 입금");

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(adjustmentRepository.save(any(CashAdjustment.class)))
                    .willAnswer(inv -> {
                        CashAdjustment a = inv.getArgument(0);
                        a.setId(1L);
                        return a;
                    });

            // when
            CashAdjustment result = cashAdjustmentService.recordAdjustment(10L, request, recorder);

            // then
            assertThat(result.getAdjustmentType()).isEqualTo(AdjustmentType.DEPOSIT);
            assertThat(result.getCurrency()).isEqualTo("KRW");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(result.getRecordedBy()).isEqualTo(recorder);
            assertThat(result.getMemo()).isEqualTo("초기 투자금 입금");
        }

        @Test
        @DisplayName("출금(WITHDRAWAL) 조정을 성공적으로 기록한다")
        void recordWithdrawal_success() {
            // given
            CreateCashAdjustmentRequest request = new CreateCashAdjustmentRequest();
            request.setAdjustmentType("WITHDRAWAL");
            request.setCurrency("USD");
            request.setAmount(new BigDecimal("500.50"));
            request.setMemo("수수료 출금");

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(adjustmentRepository.save(any(CashAdjustment.class)))
                    .willAnswer(inv -> {
                        CashAdjustment a = inv.getArgument(0);
                        a.setId(2L);
                        return a;
                    });

            // when
            CashAdjustment result = cashAdjustmentService.recordAdjustment(10L, request, recorder);

            // then
            assertThat(result.getAdjustmentType()).isEqualTo(AdjustmentType.WITHDRAWAL);
            assertThat(result.getCurrency()).isEqualTo("USD");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("500.50"));
        }

        @Test
        @DisplayName("BigDecimal 금액이 정확히 보존된다")
        void recordAdjustment_bigDecimalPrecision() {
            // given
            CreateCashAdjustmentRequest request = new CreateCashAdjustmentRequest();
            request.setAdjustmentType("DEPOSIT");
            request.setCurrency("USDT");
            request.setAmount(new BigDecimal("12345.67"));

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(adjustmentRepository.save(any(CashAdjustment.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            CashAdjustment result = cashAdjustmentService.recordAdjustment(10L, request, recorder);

            // then
            ArgumentCaptor<CashAdjustment> captor = ArgumentCaptor.forClass(CashAdjustment.class);
            verify(adjustmentRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount().compareTo(new BigDecimal("12345.67"))).isZero();
        }

        @Test
        @DisplayName("잘못된 adjustmentType이면 IllegalArgumentException을 던진다")
        void recordAdjustment_invalidType_throws() {
            // given
            CreateCashAdjustmentRequest request = new CreateCashAdjustmentRequest();
            request.setAdjustmentType("INVALID");
            request.setCurrency("KRW");
            request.setAmount(new BigDecimal("100"));

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);

            // when & then
            assertThatThrownBy(() -> cashAdjustmentService.recordAdjustment(10L, request, recorder))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========== getAdjustments ==========

    @Nested
    @DisplayName("getAdjustments — 조정 내역 조회")
    class GetAdjustments {

        @Test
        @DisplayName("팀의 조정 내역을 조회한다")
        void getAdjustments_success() {
            // given
            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(adjustmentRepository.findByTeamIdOrderByCreatedAtDesc(10L))
                    .willReturn(Collections.emptyList());

            // when
            List<CashAdjustment> result = cashAdjustmentService.getAdjustments(10L);

            // then
            assertThat(result).isEmpty();
            verify(adjustmentRepository).findByTeamIdOrderByCreatedAtDesc(10L);
        }
    }
}
