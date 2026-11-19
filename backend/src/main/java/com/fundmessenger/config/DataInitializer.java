package com.fundmessenger.config;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.entity.WritePermission;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.user.entity.SocialProvider;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DEV 환경 시드 데이터 생성 (app.environment=development 일 때만 실행)
 * - 학교 5개, 동아리 7개
 * - 기본 게시판 (공지/자유/AI) + 팀 게시판
 * - 시스템 세미카테고리 ("칼럼")
 * - 태그 정의 (회장/일반부원 + 팀 태그)
 * - DEV 계정 8개 (SUPER_ADMIN + DEV_1~DEV_7) + 멤버십 + 팀
 *
 * 모든 메서드는 멱등 — 이미 있으면 스킵, 없으면 생성.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.environment", havingValue = "development")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SchoolRepository schoolRepository;
    private final ClubRepository clubRepository;
    private final BoardRepository boardRepository;
    private final TeamRepository teamRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final SemiCategoryRepository semiCategoryRepository;
    private final UserRepository userRepository;
    private final ClubMembershipRepository membershipRepository;
    private final MemberTagRepository memberTagRepository;
    private final CarouselMemberRepository carouselMemberRepository;
    private final ChatRoomService chatRoomService;
    private final ChatMemberService chatMemberService;

    @Override
    @Transactional
    public void run(String... args) {
        migrateTeamBoardsToCustom();
        seedSchoolsAndClubs();
        seedDefaultBoards();
        seedSystemSemiCategories();
        seedTagDefinitions();
        seedDevAccounts();
        migrateExecutivePermissions();
        ensureChatRooms();
    }

    // =================== 레거시 마이그레이션 (Board.team FK 제거 완료 — 프로덕션 데이터 없으므로 no-op) ===================

    private void migrateTeamBoardsToCustom() {
        // Board.team_id, BoardType.TEAM, WritePermission.TEAM_ONLY 모두 제거됨 (OF-038-1)
        // 프로덕션 배포 전이므로 마이그레이션 대상 데이터 없음
    }

    // =================== 학교 + 동아리 ===================

    private void seedSchoolsAndClubs() {
        if (schoolRepository.count() > 0) return;

        log.info("시드 데이터 생성 시작...");

        School snu = schoolRepository.save(School.builder().name("서울대학교").build());
        School yonsei = schoolRepository.save(School.builder().name("연세대학교").build());
        School korea = schoolRepository.save(School.builder().name("고려대학교").build());
        School hanyang = schoolRepository.save(School.builder().name("한양대학교").build());
        School skku = schoolRepository.save(School.builder().name("성균관대학교").build());

        clubRepository.save(Club.builder().name("SNU 투자연구회").school(snu).build());
        clubRepository.save(Club.builder().name("서울대 주식동아리").school(snu).build());
        clubRepository.save(Club.builder().name("연세 VALUE").school(yonsei).build());
        clubRepository.save(Club.builder().name("고대 STOCK").school(korea).build());
        clubRepository.save(Club.builder().name("KUBS 투자동아리").school(korea).build());
        clubRepository.save(Club.builder().name("한양 투자클럽").school(hanyang).build());
        clubRepository.save(Club.builder().name("SKKU 금융연구회").school(skku).build());

        log.info("시드 데이터 생성 완료: 학교 {}개, 동아리 {}개",
                schoolRepository.count(), clubRepository.count());
    }

    // =================== 기본 게시판 ===================

    private void seedDefaultBoards() {
        List<Club> clubs = clubRepository.findAll();
        for (Club club : clubs) {
            createBoardIfMissing(club, "자유게시판", BoardType.CUSTOM, WritePermission.EVERYONE, 1, "자유롭게 글을 작성하세요");

            if (boardRepository.findByClubIdAndBoardType(club.getId(), BoardType.AI).isEmpty()) {
                boardRepository.save(Board.builder()
                        .name("AI").club(club).boardType(BoardType.AI)
                        .writePermission(WritePermission.EVERYONE).displayOrder(2)
                        .description("AI가 생성한 글과 재조명글").build());
            }

        }
        log.info("기본 게시판 시드 확인/생성 완료");
    }

    private void createBoardIfMissing(Club club, String name, BoardType type, WritePermission perm, int order, String desc) {
        if (boardRepository.findByClubIdAndName(club.getId(), name).isEmpty()) {
            boardRepository.save(Board.builder()
                    .name(name).club(club).boardType(type)
                    .writePermission(perm).displayOrder(order).description(desc).build());
        }
    }

    // =================== 시스템 세미카테고리 ===================

    private void seedSystemSemiCategories() {
        for (Club club : clubRepository.findAll()) {
            if (semiCategoryRepository.findByClubIdAndName(club.getId(), "칼럼").isEmpty()) {
                semiCategoryRepository.save(SemiCategory.builder()
                        .club(club).name("칼럼").displayOrder(0)
                        .generation(false).systemTag(true).enabled(true).build());
            }
        }
        log.info("시스템 세미카테고리 시드 완료");
    }

    // =================== 태그 정의 ===================

    private void seedTagDefinitions() {
        for (Club club : clubRepository.findAll()) {
            createTagDefIfMissing(club, "회장", TagType.SYSTEM, PermissionLevel.REGULAR, null, 0);
            createTagDefIfMissing(club, "일반부원", TagType.SYSTEM, PermissionLevel.REGULAR, null, 100);

            for (Team team : teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(club.getId())) {
                createTagDefIfMissing(club, team.getName() + " 팀장", TagType.TEAM_LEADER, PermissionLevel.REGULAR, team, 10);
                createTagDefIfMissing(club, team.getName() + " 팀원", TagType.TEAM_MEMBER, PermissionLevel.REGULAR, team, 20);
            }
        }
        log.info("태그 정의 시드 완료");
    }

    private void createTagDefIfMissing(Club club, String name, TagType type, PermissionLevel perm, Team team, int order) {
        if (!tagDefinitionRepository.existsByClubIdAndName(club.getId(), name)) {
            tagDefinitionRepository.save(TagDefinition.builder()
                    .club(club).name(name).tagType(type).permissionLevel(perm)
                    .team(team).displayOrder(order).build());
        }
    }

    // =================== DEV 계정 + 멤버십 + 팀 ===================

    /**
     * DEV 계정 시드 — E2E 테스트용
     *
     * | 계정 | 역할 | 상태 |
     * |------|------|------|
     * | SUPER_ADMIN | 시스템 관리자 | superAdmin=true, 동아리 미소속 |
     * | DEV_1 (홍길동) | 연세VALUE 회장 | APPROVED + 회장태그 |
     * | DEV_2 (Kim Seoyeon) | ALUMNI | APPROVED |
     * | DEV_3 (Lee Junho) | Growth Fund 팀장 | APPROVED + 팀장태그 |
     * | DEV_4 (Park Minji) | 일반부원 | APPROVED |
     * | DEV_5 (Choi Hyunwoo) | Research 팀장 | APPROVED + 팀장태그 |
     * | DEV_6 (테스트유저6) | ALUMNI | APPROVED |
     * | DEV_7 | 미소속 | NO_CLUB (User만) |
     */
    private void seedDevAccounts() {
        Club club = clubRepository.findAll().stream()
                .filter(c -> c.getName().contains("VALUE"))
                .findFirst().orElse(null);
        if (club == null) {
            log.warn("seedDevAccounts 스킵: '연세 VALUE' 동아리 없음");
            return;
        }

        // SUPER_ADMIN
        findOrCreateUser("dev-super_admin@fundmessenger.dev", "dev-super_admin", true);

        // 팀 2개 생성 (멱등)
        Team growthFund = findOrCreateTeam(club, "Growth Fund");
        Team research = findOrCreateTeam(club, "Research");

        // 팀 게시판 + 팀 태그 정의 (seedDefaultBoards, seedTagDefinitions가 이미 처리하지만 팀이 뒤늦게 생성되므로 보강)
        seedDefaultBoards();
        seedTagDefinitions();

        // DEV 계정 생성 + 멤버십
        User dev1 = createDevMember(club, "dev_1", "홍길동");
        createDevMember(club, "dev_2", "Kim Seoyeon");
        User dev3 = createDevMember(club, "dev_3", "Lee Junho");
        createDevMember(club, "dev_4", "Park Minji");
        User dev5 = createDevMember(club, "dev_5", "Choi Hyunwoo");
        createDevMember(club, "dev_6", "테스트유저6");
        findOrCreateUser("dev-dev_7@fundmessenger.dev", "dev-dev_7", false); // 미소속

        // DEV_1 = 회장 + Growth Fund 팀원 (바로가기에 팀 표시)
        assignMemberTag(dev1, club, "회장", TagType.SYSTEM, PermissionLevel.REGULAR, null);
        // 관리자 권한 직접 부여
        membershipRepository.findByUserIdAndClubId(dev1.getId(), club.getId())
                .ifPresent(m -> m.setExecutive(true));
        assignMemberTag(dev1, club, growthFund.getName() + " 팀원", TagType.TEAM_MEMBER, PermissionLevel.REGULAR, growthFund);

        // DEV_3 = Growth Fund 팀장
        assignMemberTag(dev3, club, growthFund.getName() + " 팀장", TagType.TEAM_LEADER, PermissionLevel.REGULAR, growthFund);

        // DEV_5 = Research 팀장
        assignMemberTag(dev5, club, research.getName() + " 팀장", TagType.TEAM_LEADER, PermissionLevel.REGULAR, research);

        log.info("DEV 계정 시드 완료: SUPER_ADMIN + DEV_1~DEV_7, 팀 2개 (Growth Fund, Research)");
    }

    private User findOrCreateUser(String email, String providerId, boolean superAdmin) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .socialProvider(SocialProvider.DEV)
                        .socialProviderId(providerId)
                        .superAdmin(superAdmin).build()));
    }

    private User createDevMember(Club club, String key, String name) {
        User user = findOrCreateUser("dev-" + key + "@fundmessenger.dev", "dev-" + key, false);

        if (membershipRepository.findByUserId(user.getId()).isEmpty()) {
            membershipRepository.save(ClubMembership.builder()
                    .user(user).club(club)
                    .name(name)
                    .studentId("2024" + String.format("%06d", user.getId()))
                    .phone("010-0000-" + String.format("%04d", user.getId()))
                    .status(MemberStatus.APPROVED).build());
        }
        return user;
    }

    private Team findOrCreateTeam(Club club, String name) {
        return teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(club.getId()).stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst()
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name(name).club(club).teamType(TeamType.CUSTOM).build()));
    }

    private void assignMemberTag(User user, Club club, String tagName, TagType tagType, PermissionLevel perm, Team team) {
        ClubMembership membership = membershipRepository.findByUserIdAndClubId(user.getId(), club.getId()).orElse(null);
        if (membership == null) return;

        boolean exists = memberTagRepository.findAll().stream()
                .anyMatch(mt -> mt.getClubMembership().getId().equals(membership.getId()) && tagName.equals(mt.getName()));
        if (exists) return;

        memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership).club(club)
                .name(tagName).tagType(tagType).permissionLevel(perm)
                .team(team).displayOrder(0).build());
    }

    // =================== 마이그레이션: EXECUTIVE 권한 + TEAM_LEADER permissionLevel ===================

    private void migrateExecutivePermissions() {
        // 1. SYSTEM '회장' 태그 보유자 → executive=true + CarouselMember 생성
        memberTagRepository.findAll().stream()
                .filter(mt -> mt.getTagType() == TagType.SYSTEM && "회장".equals(mt.getName()))
                .forEach(mt -> {
                    ClubMembership cm = mt.getClubMembership();
                    if (!cm.isExecutive()) {
                        cm.setExecutive(true);
                        log.info("마이그레이션: {}에게 executive=true 부여", cm.getName());
                    }
                    // CarouselMember 생성
                    if (!carouselMemberRepository.existsByClubIdAndClubMembershipId(cm.getClub().getId(), cm.getId())) {
                        carouselMemberRepository.save(CarouselMember.builder()
                                .club(cm.getClub())
                                .clubMembership(cm)
                                .roleName("회장")
                                .displayOrder(0)
                                .build());
                        log.info("마이그레이션: {} 캐러셀 멤버 등록", cm.getName());
                    }
                    // 시스템 팀(회장단)에도 동기화
                    ensureSystemTeamMember(cm);
                });

        // 2. 모든 TEAM_LEADER permissionLevel → REGULAR
        memberTagRepository.findAll().stream()
                .filter(mt -> mt.getTagType() == TagType.TEAM_LEADER && mt.getPermissionLevel() == PermissionLevel.EXECUTIVE)
                .forEach(mt -> {
                    mt.setPermissionLevel(PermissionLevel.REGULAR);
                    log.info("마이그레이션: TEAM_LEADER '{}' permissionLevel → REGULAR", mt.getName());
                });

        // 3. TagDefinition TEAM_LEADER permissionLevel → REGULAR
        tagDefinitionRepository.findAll().stream()
                .filter(td -> td.getTagType() == TagType.TEAM_LEADER && td.getPermissionLevel() == PermissionLevel.EXECUTIVE)
                .forEach(td -> {
                    td.setPermissionLevel(PermissionLevel.REGULAR);
                });

        // 4. SYSTEM '회장' TagDefinition permissionLevel → REGULAR
        tagDefinitionRepository.findAll().stream()
                .filter(td -> td.getTagType() == TagType.SYSTEM && "회장".equals(td.getName()) && td.getPermissionLevel() == PermissionLevel.EXECUTIVE)
                .forEach(td -> {
                    td.setPermissionLevel(PermissionLevel.REGULAR);
                });

        // 5. TeamType EXECUTIVE/ANALYSIS/REAL_ESTATE → CUSTOM (안전장치)
        teamRepository.findAll().stream()
                .filter(t -> t.getTeamType() != TeamType.FUND && t.getTeamType() != TeamType.CUSTOM)
                .forEach(t -> t.setTeamType(TeamType.CUSTOM));
    }

    // =================== 채팅방 보정 (기존 동아리/팀 대상 one-time) ===================

    private void ensureChatRooms() {
        for (Club club : clubRepository.findAll()) {
            // 동아리 채팅방 (ensureClubChat 내부에서 APPROVED 멤버 전원 자동 참여)
            chatRoomService.ensureClubChat(club);

            // 팀 채팅방 + 기존 팀 멤버 참여
            for (Team team : teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(club.getId())) {
                ChatRoom teamChat = chatRoomService.ensureTeamChat(club, team);
                memberTagRepository.findAll().stream()
                        .filter(mt -> mt.getTeam() != null && mt.getTeam().getId().equals(team.getId()))
                        .filter(mt -> mt.getTagType() == TagType.TEAM_LEADER || mt.getTagType() == TagType.TEAM_MEMBER)
                        .forEach(mt -> chatMemberService.addMemberToRoom(teamChat, mt.getClubMembership()));
            }
        }
        log.info("채팅방 보정 완료");
    }

    private void ensureSystemTeamMember(ClubMembership cm) {
        Long clubId = cm.getClub().getId();
        Team systemTeam = teamRepository.findByClubIdAndSystemTeamTrue(clubId).orElse(null);
        if (systemTeam == null) return;
        if (memberTagRepository.existsByClubMembershipIdAndTeamId(cm.getId(), systemTeam.getId())) return;
        memberTagRepository.save(MemberTag.builder()
                .clubMembership(cm).club(cm.getClub())
                .name("회장단 팀원").tagType(TagType.TEAM_MEMBER)
                .permissionLevel(PermissionLevel.REGULAR)
                .team(systemTeam).displayOrder(20).build());
        log.info("마이그레이션: {} 시스템 팀(회장단) 멤버 추가", cm.getName());
    }
}
