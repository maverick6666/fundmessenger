package com.fundmessenger.club.service;

import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.dto.ClubMemberResponse;
import com.fundmessenger.club.dto.MyClubInfoResponse;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.drive.repository.DriveFileRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ClubMemberServiceTest {

    @Mock private ClubMembershipRepository membershipRepository;
    @Mock private MemberTagRepository memberTagRepository;
    @Mock private TagDefinitionRepository tagDefinitionRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ClubSettingsRepository settingsRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private PostRepository postRepository;
    @Mock private DriveFileRepository driveFileRepository;

    @InjectMocks private ClubMemberService clubMemberService;

    private User adminUser, memberUser, targetUser;
    private Club club;
    private ClubMembership adminMembership, regularMembership, targetMembership;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");

        adminUser = TestFixtures.user(10L, "admin@test.dev");
        memberUser = TestFixtures.user(20L, "member@test.dev");
        targetUser = TestFixtures.user(30L, "target@test.dev");

        adminMembership = TestFixtures.approvedMembership(100L, adminUser, club);
        adminMembership.setExecutive(true);  // OF-028: executive 권한은 직접 필드
        regularMembership = TestFixtures.approvedMembership(200L, memberUser, club);
        targetMembership = TestFixtures.approvedMembership(300L, targetUser, club);
    }

    // ========== getMembers ==========

    @Nested
    @DisplayName("getMembers")
    class GetMembers {

        @Test
        @DisplayName("동아리 멤버 목록을 태그와 함께 반환한다")
        void returnsMembersWithTags() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(adminMembership);

            // bot=false 멤버 2명
            given(membershipRepository.findByClubIdAndStatus(1L, MemberStatus.APPROVED))
                    .willReturn(List.of(adminMembership, regularMembership));

            MemberTag adminTag = TestFixtures.executiveTag(1L, adminMembership, club);
            MemberTag regularTag = TestFixtures.regularTag(2L, regularMembership, club);

            given(memberTagRepository.findByClubMembershipIdIn(List.of(100L, 200L)))
                    .willReturn(List.of(adminTag, regularTag));

            // when
            List<ClubMemberResponse> result = clubMemberService.getMembers(1L, 10L);

            // then
            assertThat(result).hasSize(2);
            // 첫번째 멤버 (admin)가 executive 태그를 가짐
            assertThat(result.get(0).isExecutive()).isTrue();
            assertThat(result.get(1).isExecutive()).isFalse();
        }
    }

    // ========== getMyClubInfo ==========

    @Nested
    @DisplayName("getMyClubInfo")
    class GetMyClubInfo {

        @Test
        @DisplayName("내 동아리 정보를 반환한다")
        void returnsMyClubInfo() {
            // given
            given(membershipRepository.findByUserIdAndStatus(10L, MemberStatus.APPROVED))
                    .willReturn(List.of(adminMembership));

            given(membershipRepository.findByClubIdAndStatus(1L, MemberStatus.APPROVED))
                    .willReturn(List.of(adminMembership, regularMembership));
            given(teamRepository.countByClubIdAndDeletedFalse(1L)).willReturn(2L);

            MemberTag execTag = TestFixtures.executiveTag(1L, adminMembership, club);
            given(memberTagRepository.findByClubMembershipId(100L)).willReturn(List.of(execTag));

            given(settingsRepository.findById(1L)).willReturn(Optional.empty());
            given(postRepository.countByAuthorId(100L)).willReturn(5L);
            given(driveFileRepository.countByClubIdAndUploaderId(1L, 100L)).willReturn(3L);

            // when
            MyClubInfoResponse result = clubMemberService.getMyClubInfo(10L);

            // then
            assertThat(result.getClubId()).isEqualTo(1L);
            assertThat(result.getClubName()).isEqualTo("테스트동아리");
            assertThat(result.getMyName()).isEqualTo("admin");
            assertThat(result.isExecutive()).isTrue();
            assertThat(result.getTotalMembers()).isEqualTo(2);
            assertThat(result.getTotalTeams()).isEqualTo(2);
            assertThat(result.getMyPostCount()).isEqualTo(5);
            assertThat(result.getMyFileCount()).isEqualTo(3);
        }
    }

    // ========== deactivateMember ==========

    @Nested
    @DisplayName("deactivateMember")
    class DeactivateMember {

        @Test
        @DisplayName("멤버를 비활성화하면 INACTIVE로 변경되고 태그가 제거된다")
        void changesStatusToInactiveAndRemovesTags() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(membershipRepository.findById(300L)).willReturn(Optional.of(targetMembership));

            MemberTag tag = TestFixtures.regularTag(5L, targetMembership, club);
            given(memberTagRepository.findByClubMembershipId(300L)).willReturn(List.of(tag));

            // when
            clubMemberService.deactivateMember(1L, 300L, 10L);

            // then
            assertThat(targetMembership.getStatus()).isEqualTo(MemberStatus.INACTIVE);
            then(memberTagRepository).should().deleteAll(List.of(tag));
            then(membershipRepository).should().save(targetMembership);
        }

        @Test
        @DisplayName("자기 자신은 비활성화할 수 없다")
        void cannotDeactivateSelf() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(membershipRepository.findById(100L)).willReturn(Optional.of(adminMembership));

            // when & then
            assertThatThrownBy(() -> clubMemberService.deactivateMember(1L, 100L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("자기 자신");
        }
    }

    // ========== deleteMember ==========

    @Nested
    @DisplayName("deleteMember")
    class DeleteMember {

        @Test
        @DisplayName("멤버를 삭제하면 태그와 멤버십이 모두 제거된다")
        void deletesMembershipAndTags() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(membershipRepository.findById(300L)).willReturn(Optional.of(targetMembership));

            MemberTag tag = TestFixtures.regularTag(5L, targetMembership, club);
            given(memberTagRepository.findByClubMembershipId(300L)).willReturn(List.of(tag));

            // when
            clubMemberService.deleteMember(1L, 300L, 10L);

            // then
            then(memberTagRepository).should().deleteAll(List.of(tag));
            then(membershipRepository).should().delete(targetMembership);
        }

        @Test
        @DisplayName("자기 자신은 삭제할 수 없다")
        void cannotDeleteSelf() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(membershipRepository.findById(100L)).willReturn(Optional.of(adminMembership));

            // when & then
            assertThatThrownBy(() -> clubMemberService.deleteMember(1L, 100L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("자기 자신");
        }

        @Test
        @DisplayName("다른 동아리의 멤버를 삭제하면 SecurityException 발생")
        void cannotDeleteMemberFromOtherClub() {
            // given
            Club otherClub = TestFixtures.club(99L, "다른동아리");
            User otherUser = TestFixtures.user(99L, "other@test.dev");
            ClubMembership otherMembership = TestFixtures.approvedMembership(999L, otherUser, otherClub);

            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(membershipRepository.findById(999L)).willReturn(Optional.of(otherMembership));

            // when & then
            assertThatThrownBy(() -> clubMemberService.deleteMember(1L, 999L, 10L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("해당 동아리의 멤버가 아닙니다");
        }
    }
}
