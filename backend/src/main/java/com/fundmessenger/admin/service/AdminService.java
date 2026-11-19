package com.fundmessenger.admin.service;

import com.fundmessenger.admin.dto.GlobalSettingsResponse;
import com.fundmessenger.admin.dto.MembershipResponse;
import com.fundmessenger.admin.dto.UpdateGlobalSettingsRequest;
import com.fundmessenger.admin.entity.GlobalSettings;
import com.fundmessenger.admin.repository.GlobalSettingsRepository;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.entity.WritePermission;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.CreditHistoryRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.SchoolRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ClubMembershipRepository membershipRepository;
    private final MemberTagRepository memberTagRepository;
    private final ClubRepository clubRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final GlobalSettingsRepository globalSettingsRepository;
    private final AiGenerationJobRepository aiJobRepository;
    private final BoardRepository boardRepository;
    private final SemiCategoryRepository semiCategoryRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final ChatRoomService chatRoomService;
    private final ChatMemberService chatMemberService;

    // ========== 학교 관리 ==========

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSchoolsWithStats(Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return schoolRepository.findAll().stream()
                .map(school -> {
                    long clubCount = clubRepository.countBySchoolId(school.getId());
                    return Map.<String, Object>of(
                            "id", school.getId(),
                            "name", school.getName(),
                            "clubCount", clubCount
                    );
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> createSchool(String name, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("학교명을 입력해주세요");
        }
        School school = schoolRepository.save(School.builder().name(name.trim()).build());
        log.info("학교 등록: id={}, name={}", school.getId(), school.getName());
        return Map.of("id", school.getId(), "name", school.getName(), "clubCount", 0);
    }

    // ========== 동아리 관리 ==========

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClubsWithStats(Long schoolId, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return clubRepository.findBySchoolId(schoolId).stream()
                .map(club -> {
                    long totalMembers = membershipRepository.findByClubIdAndStatus(club.getId(), MemberStatus.APPROVED).size();
                    long pendingCount = membershipRepository.findByClubIdAndStatus(club.getId(), MemberStatus.PENDING).size();
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", club.getId());
                    map.put("name", club.getName());
                    map.put("totalMembers", totalMembers);
                    map.put("pendingCount", pendingCount);
                    map.put("subscriptionStatus", club.getSubscriptionStatus().name());
                    map.put("subscriptionStartDate", club.getSubscriptionStartDate());
                    map.put("subscriptionEndDate", club.getSubscriptionEndDate());
                    map.put("aiUsageCount", aiJobRepository.countByClubIdAndStatus(club.getId(), AiGenerationJob.JobStatus.COMPLETED));
                    return map;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> createClub(Long schoolId, String name, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("동아리명을 입력해주세요");
        }
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("학교를 찾을 수 없습니다: " + schoolId));
        Club club = clubRepository.save(Club.builder().name(name.trim()).school(school).build());
        initializeClubDefaults(club);
        log.info("동아리 등록: id={}, name={}, school={}", club.getId(), club.getName(), school.getName());
        Map<String, Object> result = new HashMap<>();
        result.put("id", club.getId());
        result.put("name", club.getName());
        result.put("totalMembers", 0);
        result.put("pendingCount", 0);
        result.put("subscriptionStatus", club.getSubscriptionStatus().name());
        result.put("subscriptionStartDate", null);
        result.put("subscriptionEndDate", null);
        result.put("aiUsageCount", 0);
        return result;
    }

    // ========== 구독 관리 ==========

    @Transactional
    public Map<String, Object> activateSubscription(Long clubId, LocalDate startDate, LocalDate endDate, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일을 모두 입력해주세요");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 이후여야 합니다");
        }

        club.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        club.setSubscriptionStartDate(startDate);
        club.setSubscriptionEndDate(endDate);

        log.info("구독 활성화: club={}, period={} ~ {}", club.getName(), startDate, endDate);

        return buildSubscriptionResponse(club);
    }

    @Transactional
    public Map<String, Object> extendSubscription(Long clubId, LocalDate newEndDate, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        if (club.getSubscriptionStatus() == SubscriptionStatus.INACTIVE) {
            throw new IllegalStateException("구독이 활성화되지 않은 동아리입니다. 먼저 구독을 활성화해주세요.");
        }
        if (newEndDate == null) {
            throw new IllegalArgumentException("새 종료일을 입력해주세요");
        }
        if (!newEndDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("새 종료일은 오늘 이후여야 합니다");
        }

        club.setSubscriptionEndDate(newEndDate);
        // 만료 상태였으면 다시 활성화
        if (club.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            club.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        }

        log.info("구독 연장: club={}, newEndDate={}", club.getName(), newEndDate);

        return buildSubscriptionResponse(club);
    }

    @Transactional
    public Map<String, Object> deactivateSubscription(Long clubId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        club.setSubscriptionStatus(SubscriptionStatus.INACTIVE);
        club.setSubscriptionStartDate(null);
        club.setSubscriptionEndDate(null);

        log.info("구독 해지: club={}", club.getName());

        return buildSubscriptionResponse(club);
    }

    /**
     * 만료일이 지난 ACTIVE 구독을 EXPIRED로 변경 (스케줄러에서 호출)
     */
    @Transactional
    public int expireOverdueSubscriptions() {
        List<Club> overdueClubs = clubRepository
                .findBySubscriptionStatusAndSubscriptionEndDateBefore(SubscriptionStatus.ACTIVE, LocalDate.now());

        for (Club club : overdueClubs) {
            club.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            log.info("구독 자동 만료: club={}, endDate={}", club.getName(), club.getSubscriptionEndDate());
        }

        return overdueClubs.size();
    }

    private Map<String, Object> buildSubscriptionResponse(Club club) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", club.getId());
        map.put("name", club.getName());
        map.put("subscriptionStatus", club.getSubscriptionStatus().name());
        map.put("subscriptionStartDate", club.getSubscriptionStartDate());
        map.put("subscriptionEndDate", club.getSubscriptionEndDate());
        return map;
    }

    // ========== 크레딧 관리 ==========

    @Transactional
    public Map<String, Object> grantCredits(Long clubId, int amount, String description, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        if (amount <= 0) {
            throw new IllegalArgumentException("지급 크레딧은 1 이상이어야 합니다");
        }

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        club.setCreditBalance(club.getCreditBalance() + amount);

        creditHistoryRepository.save(CreditHistory.builder()
                .club(club)
                .amount(amount)
                .type(CreditHistory.CreditType.GRANT)
                .description(description != null && !description.isBlank() ? description.trim() : "SUPER_ADMIN 지급")
                .build());

        log.info("크레딧 지급: club={}, amount={}, newBalance={}", club.getName(), amount, club.getCreditBalance());

        Map<String, Object> result = new HashMap<>();
        result.put("clubId", club.getId());
        result.put("creditBalance", club.getCreditBalance());
        result.put("grantedAmount", amount);
        return result;
    }

    @Transactional
    public Map<String, Object> revokeCredits(Long clubId, int amount, String description, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        if (amount <= 0) {
            throw new IllegalArgumentException("차감 크레딧은 1 이상이어야 합니다");
        }

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        if (club.getCreditBalance() < amount) {
            throw new IllegalArgumentException("동아리 잔액이 부족합니다 (현재: " + club.getCreditBalance() + ")");
        }

        club.setCreditBalance(club.getCreditBalance() - amount);

        creditHistoryRepository.save(CreditHistory.builder()
                .club(club)
                .amount(amount)
                .type(CreditHistory.CreditType.REVOKE)
                .description(description != null && !description.isBlank() ? description.trim() : "SUPER_ADMIN 차감")
                .build());

        log.info("크레딧 차감: club={}, amount={}, newBalance={}", club.getName(), amount, club.getCreditBalance());

        Map<String, Object> result = new HashMap<>();
        result.put("clubId", club.getId());
        result.put("creditBalance", club.getCreditBalance());
        result.put("revokedAmount", amount);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCreditHistory(Long clubId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        return creditHistoryRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream()
                .map(h -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", h.getId());
                    map.put("amount", h.getAmount());
                    map.put("type", h.getType().name());
                    map.put("description", h.getDescription());
                    map.put("createdAt", h.getCreatedAt());
                    return map;
                })
                .toList();
    }

    // ========== 가입 신청 관리 ==========

    @Transactional(readOnly = true)
    public List<MembershipResponse> getPendingMemberships(Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return membershipRepository.findAllByStatus(MemberStatus.PENDING)
                .stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getPendingByClub(Long clubId, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.PENDING)
                .stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional
    public MembershipResponse approveMembership(Long membershipId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버십을 찾을 수 없습니다: " + membershipId));

        if (membership.getStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException("대기 상태인 신청만 승인할 수 있습니다. 현재 상태: " + membership.getStatus());
        }

        membership.setStatus(MemberStatus.APPROVED);
        membership.setApprovedAt(LocalDateTime.now());

        // 기본 "일반부원" SYSTEM 태그 생성
        memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(membership.getClub())
                .name("일반부원")
                .tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR)
                .build());

        // 동아리 채팅방에 자동 참여
        ChatRoom clubChat = chatRoomService.ensureClubChat(membership.getClub());
        chatMemberService.addMemberToRoom(clubChat, membership);

        log.info("멤버십 승인: id={}, user={}, club={}",
                membershipId, membership.getName(), membership.getClub().getName());

        List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membership.getId());
        return MembershipResponse.from(membership, tags);
    }

    @Transactional
    public void rejectMembership(Long membershipId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버십을 찾을 수 없습니다: " + membershipId));

        if (membership.getStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException("대기 상태인 신청만 거절할 수 있습니다. 현재 상태: " + membership.getStatus());
        }

        membership.setStatus(MemberStatus.REJECTED);

        log.info("멤버십 거절: id={}, user={}, club={}",
                membershipId, membership.getName(), membership.getClub().getName());
    }

    // ========== 대시보드 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats(Long adminUserId) {
        validateSuperAdmin(adminUserId);

        Map<String, Object> stats = new HashMap<>();

        // 학교/동아리/사용자 수
        stats.put("totalSchools", schoolRepository.count());
        stats.put("totalClubs", clubRepository.count());
        stats.put("totalUsers", userRepository.count());

        // MAU (최근 30일 활동)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        stats.put("mau", userRepository.countByLastActiveAtAfter(thirtyDaysAgo));

        // 구독 현황
        Map<String, Long> subscriptions = new HashMap<>();
        subscriptions.put("active", clubRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE));
        subscriptions.put("inactive", clubRepository.countBySubscriptionStatus(SubscriptionStatus.INACTIVE));
        subscriptions.put("expired", clubRepository.countBySubscriptionStatus(SubscriptionStatus.EXPIRED));
        stats.put("subscriptions", subscriptions);

        // 회원 현황
        Map<String, Long> members = new HashMap<>();
        members.put("approved", membershipRepository.countByStatus(MemberStatus.APPROVED));
        members.put("pending", membershipRepository.countByStatus(MemberStatus.PENDING));
        members.put("rejected", membershipRepository.countByStatus(MemberStatus.REJECTED));
        stats.put("members", members);

        // AI 사용 현황
        Map<String, Object> aiUsage = new HashMap<>();
        long totalAiUsage = aiJobRepository.countCompletedByClub().stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        aiUsage.put("totalUsage", totalAiUsage);
        stats.put("aiUsage", aiUsage);

        return stats;
    }

    // ========== 동아리 멤버 관리 ==========

    @Transactional(readOnly = true)
    public List<MembershipResponse> getClubMembers(Long clubId, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED)
                .stream()
                .map(m -> {
                    List<MemberTag> tags = memberTagRepository.findByClubMembershipId(m.getId());
                    return MembershipResponse.from(m, tags);
                })
                .toList();
    }

    /**
     * SUPER_ADMIN: 멤버의 SYSTEM 태그 변경 (일반부원 ↔ 임원급)
     * tagName: "회장", "부회장" 등 — 없으면 "일반부원"으로 리셋
     */
    @Transactional
    public MembershipResponse changeClubMemberTag(Long clubId, Long membershipId,
                                                   String tagName, String permissionLevel, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버십을 찾을 수 없습니다: " + membershipId));

        if (!membership.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 멤버가 아닙니다");
        }
        if (membership.getStatus() != MemberStatus.APPROVED) {
            throw new IllegalStateException("승인된 멤버만 태그를 변경할 수 있습니다");
        }

        // 기존 SYSTEM 태그 모두 제거 후 새 태그 생성
        List<MemberTag> systemTags = memberTagRepository.findByClubMembershipId(membership.getId())
                .stream()
                .filter(t -> t.getTagType() == TagType.SYSTEM)
                .toList();
        memberTagRepository.deleteAll(systemTags);

        PermissionLevel level = permissionLevel != null
                ? PermissionLevel.valueOf(permissionLevel)
                : PermissionLevel.REGULAR;
        String name = tagName != null && !tagName.isBlank() ? tagName.trim() : "일반부원";

        memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(membership.getClub())
                .name(name)
                .tagType(TagType.SYSTEM)
                .permissionLevel(level)
                .build());

        // 회장/부회장이면 executive 권한 부여, 일반부원이면 해제
        boolean shouldBeExecutive = "회장".equals(name) || "부회장".equals(name);
        membership.setExecutive(shouldBeExecutive);

        log.info("멤버 태그 변경 (SUPER_ADMIN): membershipId={}, name={}, tag={}, level={}, executive={}",
                membershipId, membership.getName(), name, level, shouldBeExecutive);

        List<MemberTag> allTags = memberTagRepository.findByClubMembershipId(membership.getId());
        return MembershipResponse.from(membership, allTags);
    }

    // ========== 글로벌 설정 ==========

    @Transactional(readOnly = true)
    public GlobalSettingsResponse getGlobalSettings(Long adminUserId) {
        validateSuperAdmin(adminUserId);
        GlobalSettings settings = getOrCreateGlobalSettings();
        return GlobalSettingsResponse.from(settings);
    }

    @Transactional
    public GlobalSettingsResponse updateGlobalSettings(UpdateGlobalSettingsRequest request, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        GlobalSettings settings = getOrCreateGlobalSettings();

        if (request.getDriveAllowedFormats() != null) {
            settings.setDriveAllowedFormats(request.getDriveAllowedFormats().trim().toLowerCase());
        }
        if (request.getDriveMaxFileSizeMb() != null) {
            settings.setDriveMaxFileSizeMb(request.getDriveMaxFileSizeMb());
        }
        if (request.getAiCreditCost() != null) {
            settings.setAiCreditCost(request.getAiCreditCost());
        }
        if (request.getDefaultDailyAiLimit() != null) {
            settings.setDefaultDailyAiLimit(request.getDefaultDailyAiLimit());
        }
        if (request.getDailyCommentBotMin() != null) {
            settings.setDailyCommentBotMin(request.getDailyCommentBotMin());
        }
        if (request.getDailyCommentBotMax() != null) {
            settings.setDailyCommentBotMax(request.getDailyCommentBotMax());
        }
        // min > max 방지
        if (settings.getDailyCommentBotMin() > settings.getDailyCommentBotMax()) {
            settings.setDailyCommentBotMax(settings.getDailyCommentBotMin());
        }

        log.info("글로벌 설정 업데이트: formats={}, maxSize={}MB, aiCost={}, dailyAiLimit={}, botMin={}, botMax={}",
                settings.getDriveAllowedFormats(), settings.getDriveMaxFileSizeMb(),
                settings.getAiCreditCost(), settings.getDefaultDailyAiLimit(),
                settings.getDailyCommentBotMin(), settings.getDailyCommentBotMax());

        return GlobalSettingsResponse.from(settings);
    }

    private GlobalSettings getOrCreateGlobalSettings() {
        return globalSettingsRepository.findById(1L)
                .orElseGet(() -> globalSettingsRepository.save(
                        GlobalSettings.builder().id(1L).build()
                ));
    }

    // ========== Private ==========

    public void requireSuperAdmin(Long userId) {
        validateSuperAdmin(userId);
    }

    private void validateSuperAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        if (!user.isSuperAdmin()) {
            throw new SecurityException("슈퍼어드민 권한이 필요합니다");
        }
    }

    /**
     * 동아리 생성 시 기본 구조 자동 초기화
     * - 기본 게시판 (자유게시판, AI)
     * - 시스템 세미카테고리 (칼럼)
     * - 기본 태그 정의 (회장, 일반부원)
     */
    private void initializeClubDefaults(Club club) {
        // 기본 게시판
        boardRepository.save(Board.builder()
                .name("자유게시판").club(club).boardType(BoardType.CUSTOM)
                .writePermission(WritePermission.EVERYONE).displayOrder(1)
                .description("자유롭게 글을 작성하세요").build());
        boardRepository.save(Board.builder()
                .name("AI").club(club).boardType(BoardType.AI)
                .writePermission(WritePermission.EVERYONE).displayOrder(2)
                .description("AI가 생성한 글과 재조명글").build());

        // 시스템 세미카테고리
        semiCategoryRepository.save(SemiCategory.builder()
                .club(club).name("칼럼").displayOrder(0)
                .generation(false).systemTag(true).enabled(true).build());

        // 기본 태그 정의
        tagDefinitionRepository.save(TagDefinition.builder()
                .club(club).name("회장").tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR).displayOrder(0).build());
        tagDefinitionRepository.save(TagDefinition.builder()
                .club(club).name("일반부원").tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR).displayOrder(100).build());

        log.info("동아리 기본 구조 초기화 완료: clubId={}", club.getId());
    }
}
