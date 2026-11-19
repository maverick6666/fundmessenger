package com.fundmessenger.admin.service;

import com.fundmessenger.admin.dto.GlobalSettingsResponse;
import com.fundmessenger.admin.dto.MembershipResponse;
import com.fundmessenger.admin.entity.GlobalSettings;
import com.fundmessenger.admin.repository.GlobalSettingsRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock ClubMembershipRepository membershipRepository;
    @Mock MemberTagRepository memberTagRepository;
    @Mock ClubRepository clubRepository;
    @Mock CreditHistoryRepository creditHistoryRepository;
    @Mock SchoolRepository schoolRepository;
    @Mock UserRepository userRepository;
    @Mock GlobalSettingsRepository globalSettingsRepository;

    @InjectMocks AdminService sut;

    User superAdmin;
    Club club;
    School school;

    @BeforeEach
    void setUp() {
        superAdmin = TestFixtures.superAdmin(1L);
        school = TestFixtures.school(1L, "연세대학교");
        club = TestFixtures.club(1L, "VALUE", school);
    }

    @Test
    @DisplayName("대시보드 통계 — 학교/동아리/사용자 수와 구독/회원/크레딧 현황을 반환한다")
    void getDashboardStats_returnsStats() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(superAdmin));
        given(schoolRepository.count()).willReturn(5L);
        given(clubRepository.count()).willReturn(10L);
        given(userRepository.count()).willReturn(100L);
        given(userRepository.countByLastActiveAtAfter(any(LocalDateTime.class))).willReturn(50L);
        given(clubRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE)).willReturn(8L);
        given(clubRepository.countBySubscriptionStatus(SubscriptionStatus.INACTIVE)).willReturn(1L);
        given(clubRepository.countBySubscriptionStatus(SubscriptionStatus.EXPIRED)).willReturn(1L);
        given(membershipRepository.countByStatus(MemberStatus.APPROVED)).willReturn(80L);
        given(membershipRepository.countByStatus(MemberStatus.PENDING)).willReturn(10L);
        given(membershipRepository.countByStatus(MemberStatus.REJECTED)).willReturn(5L);
        given(creditHistoryRepository.sumAmountByType(CreditHistory.CreditType.GRANT)).willReturn(1000L);
        given(creditHistoryRepository.sumAmountByType(CreditHistory.CreditType.REVOKE)).willReturn(200L);
        given(clubRepository.findAll()).willReturn(List.of(club));

        // when
        Map<String, Object> stats = sut.getDashboardStats(1L);

        // then
        assertThat(stats.get("totalSchools")).isEqualTo(5L);
        assertThat(stats.get("totalClubs")).isEqualTo(10L);
        assertThat(stats.get("totalUsers")).isEqualTo(100L);
        assertThat(stats.get("mau")).isEqualTo(50L);
    }

    @Test
    @DisplayName("가입 승인 — PENDING 멤버십이 APPROVED로 변경되고 기본 태그가 생성된다")
    void approveMembership_changesStatusToApproved() {
        // given
        User applicant = TestFixtures.user(2L, "applicant@test.dev");
        ClubMembership pending = TestFixtures.pendingMembership(10L, applicant, club);

        MemberTag regularTag = TestFixtures.regularTag(1L, pending, club);

        given(userRepository.findById(1L)).willReturn(Optional.of(superAdmin));
        given(membershipRepository.findById(10L)).willReturn(Optional.of(pending));
        given(memberTagRepository.save(any(MemberTag.class))).willReturn(regularTag);
        given(memberTagRepository.findByClubMembershipId(10L)).willReturn(List.of(regularTag));

        // when
        MembershipResponse result = sut.approveMembership(10L, 1L);

        // then
        assertThat(pending.getStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(pending.getApprovedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        then(memberTagRepository).should().save(any(MemberTag.class));
    }

    @Test
    @DisplayName("가입 거절 — PENDING 멤버십이 REJECTED로 변경된다")
    void rejectMembership_changesStatusToRejected() {
        // given
        User applicant = TestFixtures.user(3L, "reject@test.dev");
        ClubMembership pending = TestFixtures.pendingMembership(11L, applicant, club);

        given(userRepository.findById(1L)).willReturn(Optional.of(superAdmin));
        given(membershipRepository.findById(11L)).willReturn(Optional.of(pending));

        // when
        sut.rejectMembership(11L, 1L);

        // then
        assertThat(pending.getStatus()).isEqualTo(MemberStatus.REJECTED);
    }

    @Test
    @DisplayName("글로벌 설정 조회 — 기존 설정이 있으면 반환한다")
    void getGlobalSettings_returnsSettings() {
        // given
        GlobalSettings settings = GlobalSettings.builder()
                .id(1L).driveAllowedFormats("pdf").driveMaxFileSizeMb(50)
                .aiCreditCost(1).defaultDailyAiLimit(10)
                .dailyCommentBotMin(5).dailyCommentBotMax(10)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(superAdmin));
        given(globalSettingsRepository.findById(1L)).willReturn(Optional.of(settings));

        // when
        GlobalSettingsResponse result = sut.getGlobalSettings(1L);

        // then
        assertThat(result.getDriveAllowedFormats()).isEqualTo("pdf");
        assertThat(result.getAiCreditCost()).isEqualTo(1);
        assertThat(result.getDailyCommentBotMin()).isEqualTo(5);
    }
}
