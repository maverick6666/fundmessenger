package com.fundmessenger.club.service;

import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.dto.CreateTeamRequest;
import com.fundmessenger.club.dto.TeamDetailResponse;
import com.fundmessenger.club.dto.TeamResponse;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private MemberTagRepository memberTagRepository;
    @Mock private TagDefinitionRepository tagDefinitionRepository;
    @Mock private ClubMembershipRepository clubMembershipRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private ChatMemberService chatMemberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TeamService teamService;

    private User adminUser, memberUser;
    private Club club;
    private ClubMembership adminMembership, regularMembership;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");

        adminUser = TestFixtures.user(10L, "admin@test.dev");
        memberUser = TestFixtures.user(20L, "member@test.dev");

        adminMembership = TestFixtures.approvedMembership(100L, adminUser, club);
        regularMembership = TestFixtures.approvedMembership(200L, memberUser, club);
    }

    // ========== createTeam ==========

    @Nested
    @DisplayName("createTeam")
    class CreateTeam {

        @Test
        @DisplayName("팀 생성 시 게시판 + 태그 정의가 자동 생성된다")
        void createsTeamWithBoardAndTagDefinitions() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(clubRepository.findById(1L)).willReturn(Optional.of(club));
            given(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(1L)).willReturn(List.of());

            Team savedTeam = TestFixtures.team(50L, club, "성장펀드");
            given(teamRepository.save(any(Team.class))).willReturn(savedTeam);
            given(memberTagRepository.countByTeamId(50L)).willReturn(0L);
            given(memberTagRepository.findByTeamIdAndTagType(50L, TagType.TEAM_LEADER))
                    .willReturn(List.of());
            given(chatRoomService.ensureTeamChat(any(), any())).willReturn(ChatRoom.builder().build());

            CreateTeamRequest request = new CreateTeamRequest();
            request.setName("성장펀드");

            // when
            TeamResponse result = teamService.createTeam(1L, request, 10L);

            // then
            assertThat(result.getName()).isEqualTo("성장펀드");

            // 태그 정의 2개 생성 확인 (팀장, 팀원)
            ArgumentCaptor<TagDefinition> tdCaptor = ArgumentCaptor.forClass(TagDefinition.class);
            then(tagDefinitionRepository).should(times(2)).save(tdCaptor.capture());
            List<TagDefinition> savedDefs = tdCaptor.getAllValues();
            assertThat(savedDefs).extracting(TagDefinition::getName)
                    .containsExactly("성장펀드 팀장", "성장펀드 팀원");
        }

        @Test
        @DisplayName("중복 팀명이 있으면 자동 넘버링된다")
        void duplicateNameAutoNumbers() {
            // given
            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(clubRepository.findById(1L)).willReturn(Optional.of(club));

            Team existingTeam = TestFixtures.team(1L, club, "성장펀드");
            given(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(1L)).willReturn(List.of(existingTeam));

            Team savedTeam = TestFixtures.team(50L, club, "성장펀드 1");
            given(teamRepository.save(any(Team.class))).willReturn(savedTeam);
            given(memberTagRepository.countByTeamId(50L)).willReturn(0L);
            given(memberTagRepository.findByTeamIdAndTagType(50L, TagType.TEAM_LEADER))
                    .willReturn(List.of());
            given(chatRoomService.ensureTeamChat(any(), any())).willReturn(ChatRoom.builder().build());

            CreateTeamRequest request = new CreateTeamRequest();
            request.setName("성장펀드");

            // when
            TeamResponse result = teamService.createTeam(1L, request, 10L);

            // then
            ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
            then(teamRepository).should().save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("성장펀드 1");
        }
    }

    // ========== getTeams ==========

    @Nested
    @DisplayName("getTeams")
    class GetTeams {

        @Test
        @DisplayName("삭제되지 않은 팀 목록을 반환한다")
        void returnsNonDeletedTeams() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(adminMembership);

            Team team1 = TestFixtures.team(1L, club, "팀A");
            Team team2 = TestFixtures.team(2L, club, "팀B");
            given(teamRepository.findByClubIdWithGenerations(1L)).willReturn(List.of(team1, team2));
            given(memberTagRepository.findByTeamIdIn(List.of(1L, 2L))).willReturn(List.of());

            // when
            List<TeamResponse> result = teamService.getTeams(1L, 10L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(TeamResponse::getName)
                    .containsExactly("팀A", "팀B");
        }
    }

    // ========== addMembers ==========

    @Nested
    @DisplayName("addMembers")
    class AddMembers {

        @Test
        @DisplayName("팀에 멤버를 추가하면 TEAM_MEMBER 태그가 부여된다")
        void addsMemberTagToTeam() {
            // given
            Team team = TestFixtures.team(50L, club, "성장펀드");

            given(authService.validateTeamManager(10L, 1L, 50L)).willReturn(adminMembership);
            given(teamRepository.findById(50L)).willReturn(Optional.of(team));
            given(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(
                    200L, 50L, TagType.TEAM_MEMBER)).willReturn(false);
            given(memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(
                    200L, 50L, TagType.TEAM_LEADER)).willReturn(false);
            given(clubMembershipRepository.findById(200L)).willReturn(Optional.of(regularMembership));
            given(chatRoomService.ensureTeamChat(any(), any())).willReturn(ChatRoom.builder().build());

            // addMembers returns getTeam() -> needs these stubs
            given(authService.validateClubMember(10L, 1L)).willReturn(adminMembership);
            given(memberTagRepository.existsByClubMembershipIdAndTeamId(100L, 50L)).willReturn(true);
            given(memberTagRepository.findByTeamId(50L)).willReturn(List.of());

            // when
            TeamDetailResponse result = teamService.addMembers(1L, 50L, List.of(200L), 10L);

            // then
            ArgumentCaptor<MemberTag> captor = ArgumentCaptor.forClass(MemberTag.class);
            // save is called once for the team member tag
            then(memberTagRepository).should(atLeastOnce()).save(captor.capture());
            MemberTag saved = captor.getValue();
            assertThat(saved.getTagType()).isEqualTo(TagType.TEAM_MEMBER);
            assertThat(saved.getName()).isEqualTo("성장펀드 팀원");
        }
    }

    // ========== removeMember ==========

    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("팀원 태그를 삭제하여 팀에서 제거한다")
        void removesTeamMemberTag() {
            // given
            Team team = TestFixtures.team(50L, club, "성장펀드");
            MemberTag teamMemberTag = TestFixtures.teamMemberTag(300L, regularMembership, club, team);

            given(authService.validateTeamManager(10L, 1L, 50L)).willReturn(adminMembership);
            given(memberTagRepository.findById(300L)).willReturn(Optional.of(teamMemberTag));
            given(memberTagRepository.findByTeamId(50L)).willReturn(List.of());
            given(chatRoomService.findTeamChat(1L, 50L)).willReturn(Optional.empty());

            // when
            teamService.removeMember(1L, 50L, 300L, 10L);

            // then
            then(memberTagRepository).should().delete(teamMemberTag);
            then(eventPublisher).should().publishEvent(any(Object.class));
        }
    }

    // ========== changeTeamLeader ==========

    @Nested
    @DisplayName("changeTeamLeader")
    class ChangeTeamLeader {

        @Test
        @DisplayName("기존 팀장을 팀원으로, 새 멤버를 팀장으로 전환한다")
        void changesLeaderOldToMemberNewToLeader() {
            // given
            Team team = TestFixtures.team(50L, club, "성장펀드");
            MemberTag oldLeaderTag = TestFixtures.teamLeaderTag(301L, adminMembership, club, team);
            MemberTag newMemberTag = TestFixtures.teamMemberTag(302L, regularMembership, club, team);

            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(teamRepository.findById(50L)).willReturn(Optional.of(team));
            given(memberTagRepository.findByTeamIdAndTagType(50L, TagType.TEAM_LEADER))
                    .willReturn(List.of(oldLeaderTag));
            given(memberTagRepository.findByClubMembershipIdAndTeamIdAndTagType(
                    200L, 50L, TagType.TEAM_MEMBER)).willReturn(Optional.of(newMemberTag));
            given(clubMembershipRepository.findById(200L)).willReturn(Optional.of(regularMembership));

            // getTeam() called at end: needs these stubs
            given(authService.validateClubMember(10L, 1L)).willReturn(adminMembership);
            given(memberTagRepository.existsByClubMembershipIdAndTeamId(100L, 50L)).willReturn(true);
            given(memberTagRepository.findByTeamId(50L)).willReturn(List.of(oldLeaderTag, newMemberTag));

            // when
            teamService.changeTeamLeader(1L, 50L, 200L, 10L);

            // then: 기존 팀장 -> 팀원
            assertThat(oldLeaderTag.getTagType()).isEqualTo(TagType.TEAM_MEMBER);
            assertThat(oldLeaderTag.getName()).isEqualTo("성장펀드 팀원");
            assertThat(oldLeaderTag.getPermissionLevel()).isEqualTo(PermissionLevel.REGULAR);

            // then: 새 팀원 -> 팀장
            assertThat(newMemberTag.getTagType()).isEqualTo(TagType.TEAM_LEADER);
            assertThat(newMemberTag.getName()).isEqualTo("성장펀드 팀장");
            assertThat(newMemberTag.getPermissionLevel()).isEqualTo(PermissionLevel.REGULAR);
        }
    }

    // ========== dissolveTeam ==========

    @Nested
    @DisplayName("dissolveTeam")
    class DissolveTeam {

        @Test
        @DisplayName("팀을 소프트 삭제하고 관련 태그를 정리한다")
        void softDeletesTeamAndCleansUp() {
            // given
            Team team = TestFixtures.team(50L, club, "성장펀드");

            given(authService.validateClubAdmin(10L, 1L)).willReturn(adminMembership);
            given(teamRepository.findById(50L)).willReturn(Optional.of(team));

            // when
            teamService.dissolveTeam(1L, 50L, 10L);

            // then
            assertThat(team.isDeleted()).isTrue();
            then(memberTagRepository).should().deleteAllByTeamId(50L);
            then(tagDefinitionRepository).should().deleteAllByTeamId(50L);
        }
    }
}
