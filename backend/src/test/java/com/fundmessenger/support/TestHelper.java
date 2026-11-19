package com.fundmessenger.support;

import com.fundmessenger.auth.service.JwtService;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.user.entity.SocialProvider;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration 테스트용 DB 저장 헬퍼.
 * Repository를 통해 실제 DB에 엔티티를 저장한다.
 */
@Component
@ActiveProfiles("test")
public class TestHelper {

    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private ClubMembershipRepository membershipRepository;
    @Autowired private MemberTagRepository memberTagRepository;
    @Autowired private TagDefinitionRepository tagDefinitionRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private BoardRepository boardRepository;
    @Autowired private SemiCategoryRepository semiCategoryRepository;
    @Autowired private JwtService jwtService;

    // ========== User ==========

    public User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .socialProvider(SocialProvider.DEV)
                .socialProviderId("dev-" + email)
                .build());
    }

    public User createSuperAdmin() {
        return userRepository.save(User.builder()
                .email("superadmin@test.dev")
                .socialProvider(SocialProvider.DEV)
                .socialProviderId("dev-superadmin")
                .superAdmin(true)
                .build());
    }

    // ========== School + Club ==========

    public School createSchool(String name) {
        return schoolRepository.save(School.builder().name(name).build());
    }

    public Club createClub(String name, School school) {
        return clubRepository.save(Club.builder()
                .name(name)
                .school(school)
                .creditBalance(1000)
                .build());
    }

    public Club createClubWithSchool(String clubName, String schoolName) {
        School school = createSchool(schoolName);
        return createClub(clubName, school);
    }

    // ========== Membership ==========

    public ClubMembership createApprovedMembership(User user, Club club, String name) {
        ClubMembership m = ClubMembership.builder()
                .user(user)
                .club(club)
                .name(name)
                .studentId("2024" + String.format("%04d", user.getId()))
                .phone("010-0000-" + String.format("%04d", user.getId()))
                .status(MemberStatus.APPROVED)
                .build();
        return membershipRepository.save(m);
    }

    public ClubMembership createPendingMembership(User user, Club club, String name) {
        ClubMembership m = ClubMembership.builder()
                .user(user)
                .club(club)
                .name(name)
                .studentId("2024" + String.format("%04d", user.getId()))
                .phone("010-0000-" + String.format("%04d", user.getId()))
                .status(MemberStatus.PENDING)
                .build();
        return membershipRepository.save(m);
    }

    // ========== Tags ==========

    public MemberTag createExecutiveTag(ClubMembership membership, Club club) {
        // OF-028: executive 권한은 ClubMembership.executive 필드로 직접 관리
        if (!membership.isExecutive()) {
            membership.setExecutive(true);
            membershipRepository.save(membership);
        }
        return memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name("회장")
                .tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR)
                .displayOrder(0)
                .build());
    }

    public MemberTag createRegularTag(ClubMembership membership, Club club) {
        return memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name("일반부원")
                .tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR)
                .displayOrder(100)
                .build());
    }

    public MemberTag createTeamLeaderTag(ClubMembership membership, Club club, Team team) {
        // OF-028: team leaders are NOT executives — permissionLevel is REGULAR
        return memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name(team.getName() + " 팀장")
                .tagType(TagType.TEAM_LEADER)
                .permissionLevel(PermissionLevel.REGULAR)
                .team(team)
                .displayOrder(10)
                .build());
    }

    public MemberTag createTeamMemberTag(ClubMembership membership, Club club, Team team) {
        return memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name(team.getName() + " 팀원")
                .tagType(TagType.TEAM_MEMBER)
                .permissionLevel(PermissionLevel.REGULAR)
                .team(team)
                .displayOrder(20)
                .build());
    }

    // ========== Team ==========

    public Team createTeam(Club club, String name) {
        return teamRepository.save(Team.builder()
                .name(name)
                .club(club)
                .teamType(TeamType.CUSTOM)
                .build());
    }

    public Team createFundTeam(Club club, String name) {
        return teamRepository.save(Team.builder()
                .name(name)
                .club(club)
                .teamType(TeamType.FUND)
                .build());
    }

    // ========== Board ==========

    public Board createBoard(Club club, String name, WritePermission wp) {
        return boardRepository.save(Board.builder()
                .name(name)
                .club(club)
                .boardType(BoardType.CUSTOM)
                .writePermission(wp)
                .displayOrder(0)
                .build());
    }

    public Board createEveryoneBoard(Club club) {
        return createBoard(club, "자유게시판", WritePermission.EVERYONE);
    }

    public Board createExecutiveBoard(Club club) {
        Board b = boardRepository.save(Board.builder()
                .name("공지사항")
                .club(club)
                .boardType(BoardType.CUSTOM)
                .writePermission(WritePermission.EXECUTIVE_ONLY)
                .displayOrder(0)
                .build());
        return b;
    }

    public Board createTeamBoard(Club club, Team team) {
        return boardRepository.save(Board.builder()
                .name(team.getName() + " 게시판")
                .club(club)
                .boardType(BoardType.CUSTOM)
                .writePermission(WritePermission.EVERYONE)
                .displayOrder(100)
                .build());
    }

    // ========== SemiCategory ==========

    public SemiCategory createSemiCategory(Club club, String name) {
        return semiCategoryRepository.save(SemiCategory.builder()
                .club(club)
                .name(name)
                .displayOrder(0)
                .generation(false)
                .build());
    }

    public SemiCategory createGeneration(Club club, String name) {
        return semiCategoryRepository.save(SemiCategory.builder()
                .club(club)
                .name(name)
                .displayOrder(0)
                .generation(true)
                .build());
    }

    // ========== JWT ==========

    public String generateToken(User user) {
        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    public String authHeader(User user) {
        return "Bearer " + generateToken(user);
    }
}
