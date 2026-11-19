package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import com.fundmessenger.board.entity.WritePermission;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.club.dto.GenerationTransitionRequest;
import com.fundmessenger.club.dto.GenerationTransitionResponse;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final SemiCategoryRepository categoryRepository;
    private final ClubMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final BoardRepository boardRepository;
    private final ClubAuthorizationService authService;
    private final ApplicationEventPublisher eventPublisher;

    /** 기수 태그 목록 (멤버 누구나) */
    @Transactional(readOnly = true)
    public List<SemiCategoryResponse> getGenerations(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return categoryRepository.findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(clubId).stream()
                .map(SemiCategoryResponse::from)
                .toList();
    }

    /** 기수 태그 단일 생성 (회장단) */
    @Transactional
    public SemiCategoryResponse createGeneration(Long clubId, String name, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);

        String trimmed = name.trim();
        categoryRepository.findByClubIdAndName(clubId, trimmed)
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 존재하는 기수입니다: " + trimmed);
                });

        int maxOrder = categoryRepository.findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(clubId).stream()
                .mapToInt(SemiCategory::getDisplayOrder)
                .max().orElse(-1);

        SemiCategory gen = categoryRepository.save(SemiCategory.builder()
                .club(membership.getClub())
                .name(trimmed)
                .displayOrder(maxOrder + 1)
                .generation(true)
                .build());

        log.info("기수 태그 생성: clubId={}, name={}", clubId, trimmed);
        return SemiCategoryResponse.from(gen);
    }

    /** 기수 태그 일괄 생성 (회장단) — startNumber기 ~ endNumber기 */
    @Transactional
    public List<SemiCategoryResponse> batchCreateGenerations(Long clubId, int startNumber, int endNumber, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);

        if (startNumber > endNumber) {
            throw new IllegalArgumentException("시작 기수는 종료 기수보다 작거나 같아야 합니다");
        }

        int maxOrder = categoryRepository.findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(clubId).stream()
                .mapToInt(SemiCategory::getDisplayOrder)
                .max().orElse(-1);

        List<SemiCategoryResponse> created = new ArrayList<>();
        for (int i = startNumber; i <= endNumber; i++) {
            String name = i + "기";
            if (categoryRepository.findByClubIdAndName(clubId, name).isPresent()) {
                continue; // 이미 존재하는 기수는 건너뜀
            }
            maxOrder++;
            SemiCategory gen = categoryRepository.save(SemiCategory.builder()
                    .club(membership.getClub())
                    .name(name)
                    .displayOrder(maxOrder)
                    .generation(true)
                    .build());
            created.add(SemiCategoryResponse.from(gen));
        }

        log.info("기수 태그 일괄 생성: clubId={}, {}기~{}기, 생성={}건", clubId, startNumber, endNumber, created.size());
        return created;
    }

    /** 기수 태그 삭제 (회장단) */
    @Transactional
    public void deleteGeneration(Long clubId, Long generationId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        SemiCategory gen = categoryRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("기수를 찾을 수 없습니다: " + generationId));
        if (!gen.getClub().getId().equals(clubId) || !gen.isGeneration()) {
            throw new SecurityException("해당 동아리의 기수 태그가 아닙니다");
        }

        // M:N 연관 해제 (membership_generations, team_generations, post_semi_categories)
        membershipRepository.findByClubIdAndStatus(clubId, com.fundmessenger.club.entity.MemberStatus.APPROVED)
                .forEach(m -> m.getGenerations().remove(gen));
        teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId)
                .forEach(t -> t.getGenerations().remove(gen));

        categoryRepository.delete(gen);
        log.info("기수 태그 삭제: clubId={}, generationId={}, name={}", clubId, generationId, gen.getName());
    }

    /** 최신(현재) 기수 반환 */
    @Transactional(readOnly = true)
    public SemiCategory getCurrentGeneration(Long clubId) {
        return categoryRepository.findTopByClubIdAndGenerationTrueOrderByDisplayOrderDesc(clubId)
                .orElse(null);
    }

    /** 멤버 기수 태그 목록 */
    @Transactional(readOnly = true)
    public List<SemiCategoryResponse> getMemberGenerations(Long clubId, Long membershipId, Long userId) {
        authService.validateClubMember(userId, clubId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다: " + membershipId));

        return membership.getGenerations().stream()
                .sorted((a, b) -> a.getDisplayOrder().compareTo(b.getDisplayOrder()))
                .map(SemiCategoryResponse::from)
                .toList();
    }

    /** 멤버에 기수 태그 추가 (본인 또는 회장단) */
    @Transactional
    public void addMemberGeneration(Long clubId, Long membershipId, Long generationId, Long userId) {
        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다: " + membershipId));

        // 본인이 아니면 회장단 권한 필요
        if (!target.getUser().getId().equals(userId)) {
            authService.validateClubAdmin(userId, clubId);
        } else {
            authService.validateClubMember(userId, clubId);
        }

        SemiCategory gen = categoryRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("기수를 찾을 수 없습니다: " + generationId));
        if (!gen.getClub().getId().equals(clubId) || !gen.isGeneration()) {
            throw new SecurityException("해당 동아리의 기수 태그가 아닙니다");
        }

        target.getGenerations().add(gen);
        log.info("멤버 기수 추가: membershipId={}, generation={}", membershipId, gen.getName());
    }

    /** 멤버에서 기수 태그 제거 (회장단만) */
    @Transactional
    public void removeMemberGeneration(Long clubId, Long membershipId, Long generationId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다: " + membershipId));

        SemiCategory gen = categoryRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("기수를 찾을 수 없습니다: " + generationId));

        target.getGenerations().remove(gen);
        log.info("멤버 기수 제거: membershipId={}, generation={}", membershipId, gen.getName());
    }

    /** 팀에 기수 태그 추가 (회장단) */
    @Transactional
    public void addTeamGeneration(Long clubId, Long teamId, Long generationId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));

        SemiCategory gen = categoryRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("기수를 찾을 수 없습니다: " + generationId));

        team.getGenerations().add(gen);
        log.info("팀 기수 추가: teamId={}, generation={}", teamId, gen.getName());
    }

    /** 팀 기수 태그 목록 */
    @Transactional(readOnly = true)
    public List<SemiCategoryResponse> getTeamGenerations(Long clubId, Long teamId, Long userId) {
        authService.validateClubMember(userId, clubId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));

        return team.getGenerations().stream()
                .sorted((a, b) -> a.getDisplayOrder().compareTo(b.getDisplayOrder()))
                .map(SemiCategoryResponse::from)
                .toList();
    }

    // ========== 기수 전환 (B-4b) ==========

    /** 기수 전환 실행 — 5단계 위자드 결과 처리 */
    @Transactional
    public GenerationTransitionResponse executeTransition(Long clubId, GenerationTransitionRequest request, Long userId) {
        ClubMembership executor = authService.validateClubAdmin(userId, clubId);

        // Step 1: 새 기수 태그 생성
        String newGenName = request.getNewGenerationName();
        if (newGenName == null || newGenName.isBlank()) {
            // 자동 생성: 현재 최대 + 1
            SemiCategory current = getCurrentGeneration(clubId);
            int nextNum = current != null ? extractNumber(current.getName()) + 1 : 1;
            newGenName = nextNum + "기";
        }
        SemiCategoryResponse newGen = createGeneration(clubId, newGenName.trim(), userId);
        SemiCategory newGenEntity = categoryRepository.findById(newGen.getId()).orElseThrow();

        // Step 2: 멤버 상태 3분류
        int continuedCount = 0, alumniCount = 0, inactiveCount = 0;

        // 계속 멤버 → 새 기수 태그 추가
        if (request.getContinueMemberIds() != null) {
            for (Long mid : request.getContinueMemberIds()) {
                ClubMembership m = membershipRepository.findById(mid).orElse(null);
                if (m != null && m.getClub().getId().equals(clubId)) {
                    m.getGenerations().add(newGenEntity);
                    continuedCount++;
                }
            }
        }

        // 졸업 멤버 → ALUMNI 상태 + 졸업자 팀 배정
        Team alumniTeam = null;
        if (request.getAlumniMemberIds() != null && !request.getAlumniMemberIds().isEmpty()) {
            alumniTeam = getOrCreateAlumniTeam(clubId, executor.getClub());
            for (Long mid : request.getAlumniMemberIds()) {
                ClubMembership m = membershipRepository.findById(mid).orElse(null);
                if (m != null && m.getClub().getId().equals(clubId)) {
                    m.setStatus(MemberStatus.ALUMNI);
                    // 기존 팀 태그 제거, 졸업자 팀원 태그 부여
                    removeAllTeamTags(m);
                    addAlumniTeamTag(m, executor.getClub(), alumniTeam);
                    alumniCount++;
                }
            }
        }

        // 탈퇴 멤버 → INACTIVE 상태
        if (request.getInactiveMemberIds() != null) {
            for (Long mid : request.getInactiveMemberIds()) {
                ClubMembership m = membershipRepository.findById(mid).orElse(null);
                if (m != null && m.getClub().getId().equals(clubId)) {
                    m.setStatus(MemberStatus.INACTIVE);
                    removeAllTeamTags(m);
                    inactiveCount++;
                }
            }
        }

        // Step 3: 팀 처리
        int retainedCount = 0, dissolvedCount = 0;

        // 유지 팀 → 새 기수 태그 추가
        if (request.getRetainTeamIds() != null) {
            for (Long tid : request.getRetainTeamIds()) {
                Team team = teamRepository.findById(tid).orElse(null);
                if (team != null && team.getClub().getId().equals(clubId) && !team.isDeleted()) {
                    team.getGenerations().add(newGenEntity);
                    retainedCount++;
                }
            }
        }

        // 해체 팀 → soft delete
        if (request.getDissolveTeamIds() != null) {
            for (Long tid : request.getDissolveTeamIds()) {
                Team team = teamRepository.findById(tid).orElse(null);
                if (team != null && team.getClub().getId().equals(clubId) && !team.isDeleted()) {
                    team.setDeleted(true);
                    // 해체 팀 소속 '계속 멤버'의 팀 태그 제거
                    memberTagRepository.findByTeamId(tid).forEach(mt -> {
                        if (request.getContinueMemberIds() != null &&
                            request.getContinueMemberIds().contains(mt.getClubMembership().getId())) {
                            memberTagRepository.delete(mt);
                        }
                    });
                    dissolvedCount++;
                }
            }
        }

        // Step 4: 계속 멤버의 역할 초기화 (팀장/커스텀 EXECUTIVE → REGULAR)
        if (request.getContinueMemberIds() != null) {
            for (Long mid : request.getContinueMemberIds()) {
                resetMemberRoles(mid, clubId);
            }
        }

        // Step 5: 차기 회장 지정
        String nextPresidentName = null;
        if (request.getNextPresidentMembershipId() != null) {
            ClubMembership nextPres = membershipRepository.findById(request.getNextPresidentMembershipId())
                    .orElseThrow(() -> new IllegalArgumentException("차기 회장을 찾을 수 없습니다"));

            // 기존 회장 태그 제거 (모든 멤버에서)
            memberTagRepository.findByClubIdAndName(clubId, "회장")
                    .forEach(memberTagRepository::delete);

            // 새 회장 태그 부여 (표시용, 권한은 executive 필드로)
            memberTagRepository.save(MemberTag.builder()
                    .clubMembership(nextPres)
                    .club(nextPres.getClub())
                    .name("회장")
                    .tagType(TagType.SYSTEM)
                    .permissionLevel(PermissionLevel.REGULAR)
                    .displayOrder(0)
                    .build());
            nextPres.setExecutive(true);

            nextPresidentName = nextPres.getName();
        }

        log.info("기수 전환 완료: clubId={}, newGen={}, continued={}, alumni={}, inactive={}, retained={}, dissolved={}",
                clubId, newGenName, continuedCount, alumniCount, inactiveCount, retainedCount, dissolvedCount);

        // 알림: 기수 전환 — 계속 멤버 전원에게
        if (request.getContinueMemberIds() != null && !request.getContinueMemberIds().isEmpty()) {
            List<Long> targetUserIds = request.getContinueMemberIds().stream()
                    .map(mid -> membershipRepository.findById(mid).orElse(null))
                    .filter(m -> m != null)
                    .map(m -> m.getUser().getId())
                    .filter(uid -> !uid.equals(userId))
                    .toList();
            if (!targetUserIds.isEmpty()) {
                eventPublisher.publishEvent(NotificationEvent.ofMany(
                        clubId, targetUserIds, NotificationType.GENERATION_TRANSITION,
                        newGenName + " 기수 전환이 완료되었습니다",
                        "계속 " + continuedCount + "명, 졸업 " + alumniCount + "명, 탈퇴 " + inactiveCount + "명",
                        "/club"
                ));
            }
        }

        return GenerationTransitionResponse.builder()
                .newGenerationName(newGenName)
                .continuedMembers(continuedCount)
                .alumniMembers(alumniCount)
                .inactiveMembers(inactiveCount)
                .retainedTeams(retainedCount)
                .dissolvedTeams(dissolvedCount)
                .nextPresidentName(nextPresidentName)
                .build();
    }

    /** 졸업자 팀 조회 또는 생성 */
    private Team getOrCreateAlumniTeam(Long clubId, Club club) {
        return teamRepository.findByClubIdAndNameAndDeletedFalse(clubId, "졸업자")
                .orElseGet(() -> {
                    Team alumniTeam = teamRepository.save(Team.builder()
                            .name("졸업자")
                            .club(club)
                            .description("졸업 처리된 멤버 전용 팀")
                            .build());

                    // 졸업자 팀 태그 정의
                    tagDefinitionRepository.save(TagDefinition.builder()
                            .club(club).name("졸업자 팀원").team(alumniTeam)
                            .tagType(TagType.TEAM_MEMBER).permissionLevel(PermissionLevel.REGULAR)
                            .displayOrder(200).build());

                    log.info("졸업자 팀 생성: clubId={}, teamId={}", clubId, alumniTeam.getId());
                    return alumniTeam;
                });
    }

    /** 멤버의 모든 팀 태그(TEAM_LEADER/TEAM_MEMBER) 제거 */
    private void removeAllTeamTags(ClubMembership membership) {
        List<MemberTag> teamTags = memberTagRepository.findByClubMembershipId(membership.getId()).stream()
                .filter(mt -> mt.getTagType() == TagType.TEAM_LEADER || mt.getTagType() == TagType.TEAM_MEMBER)
                .toList();
        memberTagRepository.deleteAll(teamTags);
    }

    /** 졸업자 팀원 태그 부여 */
    private void addAlumniTeamTag(ClubMembership membership, Club club, Team alumniTeam) {
        // 이미 있으면 스킵
        if (memberTagRepository.findByClubMembershipIdAndTeamIdAndTagType(
                membership.getId(), alumniTeam.getId(), TagType.TEAM_MEMBER).isPresent()) {
            return;
        }
        memberTagRepository.save(MemberTag.builder()
                .clubMembership(membership)
                .club(club)
                .name("졸업자 팀원")
                .tagType(TagType.TEAM_MEMBER)
                .permissionLevel(PermissionLevel.REGULAR)
                .team(alumniTeam)
                .displayOrder(200)
                .build());
    }

    /** 계속 멤버 역할 초기화 — SYSTEM 태그 중 '회장' 제거, CUSTOM EXECUTIVE → REGULAR */
    private void resetMemberRoles(Long membershipId, Long clubId) {
        List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membershipId);
        for (MemberTag tag : tags) {
            // 회장/부회장 등 SYSTEM 태그 삭제
            if (tag.getTagType() == TagType.SYSTEM && !"일반부원".equals(tag.getName())) {
                memberTagRepository.delete(tag);
            }
        }
        // executive 권한 해제
        ClubMembership cm = membershipRepository.findById(membershipId).orElse(null);
        if (cm != null) {
            cm.setExecutive(false);
        }

        // 일반부원 태그 확인, 없으면 추가
        boolean hasSystemTag = memberTagRepository.findByClubMembershipId(membershipId).stream()
                .anyMatch(mt -> mt.getTagType() == TagType.SYSTEM);
        if (!hasSystemTag) {
            ClubMembership m = membershipRepository.findById(membershipId).orElse(null);
            if (m != null) {
                memberTagRepository.save(MemberTag.builder()
                        .clubMembership(m).club(m.getClub())
                        .name("일반부원").tagType(TagType.SYSTEM)
                        .permissionLevel(PermissionLevel.REGULAR).displayOrder(100)
                        .build());
            }
        }
    }

    /** 기수명에서 숫자 추출 (예: "35기" → 35) */
    private int extractNumber(String genName) {
        try {
            return Integer.parseInt(genName.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
