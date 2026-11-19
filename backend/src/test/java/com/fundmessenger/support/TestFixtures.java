package com.fundmessenger.support;

import com.fundmessenger.board.entity.*;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.user.entity.SocialProvider;
import com.fundmessenger.user.entity.User;

import java.time.LocalDateTime;
import java.util.HashSet;

/**
 * Unit 테스트용 순수 엔티티 팩토리.
 * DB 저장 없이 인메모리 객체만 생성한다.
 * id는 수동 설정 (Lombok @Setter 활용).
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ========== User ==========

    public static User user(Long id, String email) {
        User u = User.builder()
                .email(email)
                .socialProvider(SocialProvider.DEV)
                .socialProviderId("dev-" + id)
                .superAdmin(false)
                .build();
        u.setId(id);
        u.setCreatedAt(LocalDateTime.now());
        u.setLastActiveAt(LocalDateTime.now());
        return u;
    }

    public static User superAdmin(Long id) {
        User u = user(id, "superadmin@test.dev");
        u.setSuperAdmin(true);
        return u;
    }

    // ========== School ==========

    public static School school(Long id, String name) {
        School s = School.builder().name(name).build();
        s.setId(id);
        return s;
    }

    // ========== Club ==========

    public static Club club(Long id, String name, School school) {
        Club c = Club.builder()
                .name(name)
                .school(school)
                .creditBalance(1000)
                .build();
        c.setId(id);
        return c;
    }

    public static Club club(Long id, String name) {
        return club(id, name, school(1L, "테스트대학교"));
    }

    // ========== ClubMembership ==========

    public static ClubMembership membership(Long id, User user, Club club, MemberStatus status) {
        ClubMembership m = ClubMembership.builder()
                .user(user)
                .club(club)
                .name(user.getEmail().split("@")[0])
                .studentId("2024" + String.format("%04d", id))
                .phone("010-0000-" + String.format("%04d", id))
                .status(status)
                .build();
        m.setId(id);
        m.setAppliedAt(LocalDateTime.now());
        if (status == MemberStatus.APPROVED || status == MemberStatus.ALUMNI) {
            m.setApprovedAt(LocalDateTime.now());
        }
        return m;
    }

    public static ClubMembership approvedMembership(Long id, User user, Club club) {
        return membership(id, user, club, MemberStatus.APPROVED);
    }

    public static ClubMembership pendingMembership(Long id, User user, Club club) {
        return membership(id, user, club, MemberStatus.PENDING);
    }

    // ========== Team ==========

    public static Team team(Long id, Club club, String name) {
        Team t = Team.builder()
                .name(name)
                .club(club)
                .creditAllocation(0)
                .teamType(TeamType.CUSTOM)
                .deleted(false)
                .generations(new HashSet<>())
                .build();
        t.setId(id);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    public static Team fundTeam(Long id, Club club, String name) {
        Team t = team(id, club, name);
        t.setTeamType(TeamType.FUND);
        return t;
    }

    // ========== Board ==========

    public static Board board(Long id, Club club, String name, WritePermission wp) {
        Board b = Board.builder()
                .name(name)
                .club(club)
                .boardType(BoardType.CUSTOM)
                .writePermission(wp)
                .displayOrder(0)
                .build();
        b.setId(id);
        b.setCreatedAt(LocalDateTime.now());
        return b;
    }

    public static Board everyoneBoard(Long id, Club club) {
        return board(id, club, "자유게시판", WritePermission.EVERYONE);
    }

    public static Board executiveBoard(Long id, Club club) {
        Board b = board(id, club, "공지사항", WritePermission.EXECUTIVE_ONLY);
        b.setBoardType(BoardType.CUSTOM);
        return b;
    }

    public static Board teamBoard(Long id, Club club, Team team) {
        return board(id, club, team.getName() + " 게시판", WritePermission.EVERYONE);
    }

    // ========== MemberTag ==========

    public static MemberTag memberTag(Long id, ClubMembership membership, Club club,
                                       String name, TagType tagType, PermissionLevel level) {
        MemberTag mt = MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name(name)
                .tagType(tagType)
                .permissionLevel(level)
                .displayOrder(0)
                .build();
        mt.setId(id);
        mt.setCreatedAt(LocalDateTime.now());
        return mt;
    }

    public static MemberTag executiveTag(Long id, ClubMembership membership, Club club) {
        // OF-028: executive 권한은 ClubMembership.executive 필드로 직접 관리
        // 태그의 permissionLevel은 REGULAR로 변경
        membership.setExecutive(true);
        return memberTag(id, membership, club, "회장", TagType.SYSTEM, PermissionLevel.REGULAR);
    }

    public static MemberTag regularTag(Long id, ClubMembership membership, Club club) {
        return memberTag(id, membership, club, "일반부원", TagType.SYSTEM, PermissionLevel.REGULAR);
    }

    public static MemberTag teamLeaderTag(Long id, ClubMembership membership, Club club, Team team) {
        // OF-028: team leaders are NOT executives — permissionLevel is REGULAR
        MemberTag mt = memberTag(id, membership, club, team.getName() + " 팀장",
                TagType.TEAM_LEADER, PermissionLevel.REGULAR);
        mt.setTeam(team);
        return mt;
    }

    public static MemberTag teamMemberTag(Long id, ClubMembership membership, Club club, Team team) {
        MemberTag mt = memberTag(id, membership, club, team.getName() + " 팀원",
                TagType.TEAM_MEMBER, PermissionLevel.REGULAR);
        mt.setTeam(team);
        return mt;
    }

    public static MemberTag teamManagerTag(Long id, ClubMembership membership, Club club, Team team) {
        MemberTag mt = memberTag(id, membership, club, team.getName() + " 매니저",
                TagType.TEAM_MANAGER, PermissionLevel.REGULAR);
        mt.setTeam(team);
        return mt;
    }

    // ========== TagDefinition ==========

    public static TagDefinition tagDefinition(Long id, Club club, String name,
                                               TagType tagType, PermissionLevel level) {
        TagDefinition td = TagDefinition.builder()
                .club(club)
                .name(name)
                .tagType(tagType)
                .permissionLevel(level)
                .displayOrder(0)
                .build();
        td.setId(id);
        td.setCreatedAt(LocalDateTime.now());
        return td;
    }

    // ========== SemiCategory ==========

    public static SemiCategory semiCategory(Long id, Club club, String name, boolean generation) {
        SemiCategory sc = SemiCategory.builder()
                .club(club)
                .name(name)
                .displayOrder(0)
                .generation(generation)
                .systemTag(false)
                .enabled(true)
                .build();
        sc.setId(id);
        return sc;
    }
}
