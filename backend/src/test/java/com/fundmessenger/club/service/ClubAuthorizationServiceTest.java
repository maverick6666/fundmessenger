package com.fundmessenger.club.service;

import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubAuthorizationServiceTest {

    @Mock
    private ClubMembershipRepository membershipRepository;

    @Mock
    private MemberTagRepository memberTagRepository;

    @InjectMocks
    private ClubAuthorizationService authService;

    private User user;
    private Club club;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "hong@test.com");
        club = TestFixtures.club(10L, "연세VALUE");
    }

    // ========== validateClubMember ==========

    @Nested
    @DisplayName("validateClubMember")
    class ValidateClubMember {

        @Test
        @DisplayName("APPROVED 멤버는 검증 통과한다")
        void succeeds_forApprovedMember() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when
            ClubMembership result = authService.validateClubMember(1L, 10L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("ALUMNI 멤버는 검증 통과한다")
        void succeeds_forAlumniMember() {
            // given
            ClubMembership membership = TestFixtures.membership(100L, user, club, MemberStatus.ALUMNI);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when
            ClubMembership result = authService.validateClubMember(1L, 10L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("PENDING 멤버는 SecurityException이 발생한다")
        void throws_forPendingMember() {
            // given
            ClubMembership membership = TestFixtures.pendingMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when & then
            assertThatThrownBy(() -> authService.validateClubMember(1L, 10L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("멤버가 아닙니다");
        }

        @Test
        @DisplayName("멤버십이 없으면 SecurityException이 발생한다")
        void throws_whenNoMembershipFound() {
            // given
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.validateClubMember(1L, 10L))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ========== validateActiveMember ==========

    @Nested
    @DisplayName("validateActiveMember")
    class ValidateActiveMember {

        @Test
        @DisplayName("APPROVED 멤버는 검증 통과한다")
        void succeeds_forApproved() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when
            ClubMembership result = authService.validateActiveMember(1L, 10L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("ALUMNI 멤버는 SecurityException이 발생한다")
        void throws_forAlumni() {
            // given
            ClubMembership membership = TestFixtures.membership(100L, user, club, MemberStatus.ALUMNI);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when & then
            assertThatThrownBy(() -> authService.validateActiveMember(1L, 10L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("활성 멤버가 아닙니다");
        }
    }

    // ========== validateClubAdmin ==========

    @Nested
    @DisplayName("validateClubAdmin")
    class ValidateClubAdmin {

        @Test
        @DisplayName("executive=true 멤버는 검증 통과한다")
        void succeeds_forExecutiveMember() {
            // given — OF-028: executive 권한은 ClubMembership.executive 필드
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            membership.setExecutive(true);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when
            ClubMembership result = authService.validateClubAdmin(1L, 10L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("executive=false 멤버는 SecurityException이 발생한다")
        void throws_forNonExecutiveMember() {
            // given — OF-028: executive=false → 관리 권한 없음
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            // executive defaults to false
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when & then
            assertThatThrownBy(() -> authService.validateClubAdmin(1L, 10L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("임원급 권한이 필요합니다");
        }
    }

    // ========== validateTeamManager ==========

    @Nested
    @DisplayName("validateTeamManager")
    class ValidateTeamManager {

        private Team team;

        @BeforeEach
        void setUp() {
            team = TestFixtures.team(50L, club, "가치투자팀");
        }

        @Test
        @DisplayName("executive=true는 모든 팀을 관리할 수 있다")
        void succeeds_forExecutive_anyTeam() {
            // given — OF-028: executive 필드 직접 체크
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            membership.setExecutive(true);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            // when
            ClubMembership result = authService.validateTeamManager(1L, 10L, 50L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("TEAM_LEADER는 해당 팀을 관리할 수 있다")
        void succeeds_forTeamLeader() {
            // given — non-executive but team leader
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));
            when(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(100L, 50L, TagType.TEAM_LEADER))
                    .thenReturn(true);

            // when
            ClubMembership result = authService.validateTeamManager(1L, 10L, 50L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("TEAM_MANAGER는 해당 팀을 관리할 수 있다")
        void succeeds_forTeamManager() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));
            when(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(100L, 50L, TagType.TEAM_LEADER))
                    .thenReturn(false);
            when(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(100L, 50L, TagType.TEAM_MANAGER))
                    .thenReturn(true);

            // when
            ClubMembership result = authService.validateTeamManager(1L, 10L, 50L);

            // then
            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("일반 TEAM_MEMBER는 SecurityException이 발생한다")
        void throws_forRegularTeamMember() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            when(membershipRepository.findByUserIdAndClubId(1L, 10L))
                    .thenReturn(Optional.of(membership));
            when(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(100L, 50L, TagType.TEAM_LEADER))
                    .thenReturn(false);
            when(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(100L, 50L, TagType.TEAM_MANAGER))
                    .thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.validateTeamManager(1L, 10L, 50L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("팀 관리 권한이 없습니다");
        }
    }

    // ========== isExecutive ==========

    @Nested
    @DisplayName("isExecutive")
    class IsExecutive {

        @Test
        @DisplayName("executive=true이면 true를 반환한다")
        void returnsTrue_whenExecutiveFieldTrue() {
            // given — OF-028: executive 필드 직접 체크
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            membership.setExecutive(true);

            // when & then
            assertThat(authService.isExecutive(membership)).isTrue();
        }

        @Test
        @DisplayName("executive=false이면 false를 반환한다")
        void returnsFalse_whenExecutiveFieldFalse() {
            // given
            ClubMembership membership = TestFixtures.approvedMembership(100L, user, club);
            // executive defaults to false

            // when & then
            assertThat(authService.isExecutive(membership)).isFalse();
        }
    }
}
