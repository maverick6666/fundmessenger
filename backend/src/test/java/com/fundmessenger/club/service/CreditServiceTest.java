package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.AllocateCreditRequest;
import com.fundmessenger.club.dto.CreditHistoryResponse;
import com.fundmessenger.club.dto.CreditSummaryResponse;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.CreditHistoryRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberTagRepository memberTagRepository;

    @Mock
    private CreditHistoryRepository creditHistoryRepository;

    @Mock
    private ClubAuthorizationService authService;

    @InjectMocks
    private CreditService creditService;

    private User user;
    private Club club;
    private Team team;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "hong@test.com");
        club = TestFixtures.club(10L, "연세VALUE");
        club.setCreditBalance(1000);
        team = TestFixtures.team(50L, club, "가치투자팀");
        team.setCreditAllocation(200);
    }

    // ========== allocateToTeam ==========

    @Nested
    @DisplayName("allocateToTeam")
    class AllocateToTeam {

        @Test
        @DisplayName("성공: 동아리 잔액이 감소하고 팀 할당이 증가한다")
        void success_balanceDecreasesAndAllocationIncreases() {
            // given
            AllocateCreditRequest request = new AllocateCreditRequest();
            request.setTeamId(50L);
            request.setAmount(300);
            request.setDescription("3월 분배");

            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubAdmin(1L, 10L)).thenReturn(membership);
            when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(team));
            when(creditHistoryRepository.save(any(CreditHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // getCreditSummary 호출 시 필요한 mock
            when(authService.validateClubMember(1L, 10L)).thenReturn(membership);
            when(clubRepository.findById(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of(team));
            when(memberTagRepository.countDistinctMembersByTeamId(50L)).thenReturn(5L);

            // when
            creditService.allocateToTeam(10L, request, 1L);

            // then
            assertThat(club.getCreditBalance()).isEqualTo(700);   // 1000 - 300
            assertThat(team.getCreditAllocation()).isEqualTo(500); // 200 + 300
            verify(creditHistoryRepository).save(any(CreditHistory.class));
        }

        @Test
        @DisplayName("실패: 동아리 잔액이 부족하면 예외가 발생한다")
        void fails_whenInsufficientClubBalance() {
            // given
            AllocateCreditRequest request = new AllocateCreditRequest();
            request.setTeamId(50L);
            request.setAmount(2000); // 잔액 1000보다 큼

            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubAdmin(1L, 10L)).thenReturn(membership);
            when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(team));

            // when & then
            assertThatThrownBy(() -> creditService.allocateToTeam(10L, request, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("잔액이 부족합니다");

            verify(creditHistoryRepository, never()).save(any());
        }
    }

    // ========== reclaimFromTeam ==========

    @Nested
    @DisplayName("reclaimFromTeam")
    class ReclaimFromTeam {

        @Test
        @DisplayName("성공: 팀 할당이 감소하고 동아리 잔액이 증가한다")
        void success_allocationDecreasesAndBalanceIncreases() {
            // given
            AllocateCreditRequest request = new AllocateCreditRequest();
            request.setTeamId(50L);
            request.setAmount(100);

            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubAdmin(1L, 10L)).thenReturn(membership);
            when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(team));
            when(creditHistoryRepository.save(any(CreditHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // getCreditSummary 호출 시 필요한 mock
            when(authService.validateClubMember(1L, 10L)).thenReturn(membership);
            when(clubRepository.findById(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of(team));
            when(memberTagRepository.countDistinctMembersByTeamId(50L)).thenReturn(5L);

            // when
            creditService.reclaimFromTeam(10L, request, 1L);

            // then
            assertThat(team.getCreditAllocation()).isEqualTo(100);  // 200 - 100
            assertThat(club.getCreditBalance()).isEqualTo(1100);     // 1000 + 100
            verify(creditHistoryRepository).save(any(CreditHistory.class));
        }

        @Test
        @DisplayName("실패: 팀 잔액이 부족하면 예외가 발생한다")
        void fails_whenInsufficientTeamBalance() {
            // given
            AllocateCreditRequest request = new AllocateCreditRequest();
            request.setTeamId(50L);
            request.setAmount(500); // 팀 할당 200보다 큼

            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubAdmin(1L, 10L)).thenReturn(membership);
            when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(team));

            // when & then
            assertThatThrownBy(() -> creditService.reclaimFromTeam(10L, request, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("팀 잔액이 부족합니다");

            verify(creditHistoryRepository, never()).save(any());
        }
    }

    // ========== getCreditSummary ==========

    @Nested
    @DisplayName("getCreditSummary")
    class GetCreditSummary {

        @Test
        @DisplayName("동아리 잔액과 팀별 할당 정보를 올바르게 반환한다")
        void returnsCorrectTotals() {
            // given
            Team team2 = TestFixtures.team(51L, club, "퀀트팀");
            team2.setCreditAllocation(300);

            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubMember(1L, 10L)).thenReturn(membership);
            when(clubRepository.findById(10L)).thenReturn(Optional.of(club));
            when(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of(team, team2));
            when(memberTagRepository.countDistinctMembersByTeamId(50L)).thenReturn(3L);
            when(memberTagRepository.countDistinctMembersByTeamId(51L)).thenReturn(4L);

            // when
            CreditSummaryResponse result = creditService.getCreditSummary(10L, 1L);

            // then
            assertThat(result.getClubBalance()).isEqualTo(1000);
            assertThat(result.getTotalAllocated()).isEqualTo(500); // 200 + 300
            assertThat(result.getTotalBalance()).isEqualTo(1500);  // 1000 + 500
            assertThat(result.getTeams()).hasSize(2);
            assertThat(result.getTeams().get(0).getTeamName()).isEqualTo("가치투자팀");
            assertThat(result.getTeams().get(0).getCreditAllocation()).isEqualTo(200);
            assertThat(result.getTeams().get(0).getMemberCount()).isEqualTo(3L);
            assertThat(result.getTeams().get(1).getTeamName()).isEqualTo("퀀트팀");
            assertThat(result.getTeams().get(1).getCreditAllocation()).isEqualTo(300);
        }
    }

    // ========== getCreditHistory ==========

    @Nested
    @DisplayName("getCreditHistory")
    class GetCreditHistory {

        @Test
        @DisplayName("크레딧 내역 리스트를 반환한다")
        void returnsList() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(authService.validateClubMember(1L, 10L)).thenReturn(membership);

            CreditHistory history = CreditHistory.builder()
                    .club(club)
                    .team(team)
                    .amount(300)
                    .type(CreditHistory.CreditType.ALLOCATE)
                    .description("가치투자팀에 분배")
                    .createdAt(LocalDateTime.now())
                    .build();
            history.setId(1L);

            when(creditHistoryRepository.findByClubIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(history));

            // when
            List<CreditHistoryResponse> result = creditService.getCreditHistory(10L, 1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualTo(300);
            assertThat(result.get(0).getType()).isEqualTo("ALLOCATE");
            assertThat(result.get(0).getTeamName()).isEqualTo("가치투자팀");
        }
    }
}
