package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.*;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final ChatRoomService chatRoomService;
    private final ChatMemberService chatMemberService;
    private final ApplicationEventPublisher eventPublisher;

    // ========== 읽기 ==========

    @Transactional(readOnly = true)
    public List<TeamResponse> getTeams(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        List<Team> teams = teamRepository.findByClubIdWithGenerations(clubId).stream()
                .filter(t -> !t.isSystemTeam())
                .toList();
        if (teams.isEmpty()) return List.of();

        // 전체 팀 태그를 한번에 조회하여 팀별 memberCount/leaderName 계산
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        List<MemberTag> allTags = memberTagRepository.findByTeamIdIn(teamIds);

        Map<Long, Long> countMap = allTags.stream()
                .collect(Collectors.groupingBy(mt -> mt.getTeam().getId(), Collectors.counting()));
        Map<Long, String> leaderMap = allTags.stream()
                .filter(mt -> mt.getTagType() == TagType.TEAM_LEADER)
                .collect(Collectors.toMap(
                        mt -> mt.getTeam().getId(),
                        mt -> mt.getClubMembership().getName(),
                        (a, b) -> a // 중복 시 첫번째
                ));

        return teams.stream()
                .map(team -> TeamResponse.from(team,
                        countMap.getOrDefault(team.getId(), 0L),
                        leaderMap.get(team.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponse getTeam(Long clubId, Long teamId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        // 팀 페이지는 워크스페이스 — 팀 소속 멤버 또는 임원급만 접근 가능
        boolean isTeamMember = memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId);
        if (!isTeamMember && !authService.isExecutive(membership)) {
            throw new SecurityException("해당 팀의 멤버만 접근할 수 있습니다");
        }

        Team team = getTeamOrThrow(teamId, clubId);
        List<TeamMemberResponse> members = memberTagRepository.findByTeamId(teamId)
                .stream()
                .map(TeamMemberResponse::from)
                .toList();

        List<String> genNames = team.getGenerations().stream()
                .map(sc -> sc.getName())
                .sorted()
                .toList();

        return TeamDetailResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .teamType(team.getTeamType() != null ? team.getTeamType().name() : "CUSTOM")
                .creditAllocation(team.getCreditAllocation())
                .description(team.getDescription())
                .createdAt(team.getCreatedAt())
                .members(members)
                .generations(genNames)
                .build();
    }

    // ========== 팀 생성 ==========

    @Transactional
    public TeamResponse createTeam(Long clubId, CreateTeamRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다: " + clubId));

        String baseName = (request.getName() != null && !request.getName().trim().isEmpty())
                ? request.getName().trim() : "새 팀";
        String teamName = resolveUniqueTeamName(clubId, baseName);

        // teamType: FUND 요청이면 FUND, 그 외 CUSTOM
        TeamType teamType = TeamType.CUSTOM;
        if ("FUND".equalsIgnoreCase(request.getTeamType())) {
            teamType = TeamType.FUND;
        }

        Team team = teamRepository.save(Team.builder()
                .name(teamName)
                .club(club)
                .description(request.getDescription())
                .teamType(teamType)
                .build());

        log.info("팀 생성: clubId={}, teamId={}, name={}", clubId, team.getId(), team.getName());

        // 팀 태그 정의 생성
        tagDefinitionRepository.save(TagDefinition.builder()
                .club(club).name(team.getName() + " 팀장").team(team)
                .tagType(TagType.TEAM_LEADER).permissionLevel(PermissionLevel.REGULAR)
                .displayOrder(10).build());
        tagDefinitionRepository.save(TagDefinition.builder()
                .club(club).name(team.getName() + " 팀원").team(team)
                .tagType(TagType.TEAM_MEMBER).permissionLevel(PermissionLevel.REGULAR)
                .displayOrder(20).build());

        // 팀장 태그 부여 (선택)
        if (request.getLeaderMembershipId() != null) {
            ClubMembership leader = getApprovedMember(request.getLeaderMembershipId(), clubId);
            addTeamTag(leader, club, team, TagType.TEAM_LEADER, PermissionLevel.REGULAR);
        }

        // 팀 채팅방 자동 생성 + 팀장 참여
        ChatRoom teamChat = chatRoomService.ensureTeamChat(club, team);
        if (request.getLeaderMembershipId() != null) {
            ClubMembership leader = clubMembershipRepository.findById(request.getLeaderMembershipId()).orElse(null);
            if (leader != null) chatMemberService.addMemberToRoom(teamChat, leader);
        }

        long memberCount = memberTagRepository.countByTeamId(team.getId());
        String leaderName = findLeaderName(team.getId());
        return TeamResponse.from(team, memberCount, leaderName);
    }

    // ========== 팀 수정 ==========

    @Transactional
    public TeamResponse updateTeam(Long clubId, Long teamId, CreateTeamRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        Team team = getTeamOrThrow(teamId, clubId);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            team.setName(request.getName().trim());
        }
        team.setDescription(request.getDescription());

        long memberCount = memberTagRepository.countByTeamId(team.getId());
        String leaderName = findLeaderName(team.getId());
        return TeamResponse.from(team, memberCount, leaderName);
    }

    // ========== 펀드팀 지정/해제 ==========

    @Transactional
    public void setFundTeam(Long clubId, Long teamId, boolean fund, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        Team team = getTeamOrThrow(teamId, clubId);
        team.setTeamType(fund ? TeamType.FUND : TeamType.CUSTOM);
        log.info("팀 타입 변경: clubId={}, teamId={}, fund={}", clubId, teamId, fund);
    }

    // ========== 팀명 수정 ==========

    @Transactional
    public void updateTeamName(Long clubId, Long teamId, String newName, Long userId) {
        authService.validateTeamManager(userId, clubId, teamId);
        Team team = getTeamOrThrow(teamId, clubId);

        String oldName = team.getName();
        team.setName(newName.trim());

        // 팀 태그명도 업데이트 (MemberTag + TagDefinition)
        memberTagRepository.findByTeamId(teamId).forEach(tag -> {
            if (tag.getTagType() == TagType.TEAM_LEADER) {
                tag.setName(newName.trim() + " 팀장");
            } else if (tag.getTagType() == TagType.TEAM_MEMBER) {
                tag.setName(newName.trim() + " 팀원");
            }
        });

        // 태그 정의명도 함께 업데이트
        tagDefinitionRepository.findByClubIdAndName(clubId, oldName + " 팀장")
                .ifPresent(td -> td.setName(newName.trim() + " 팀장"));
        tagDefinitionRepository.findByClubIdAndName(clubId, oldName + " 팀원")
                .ifPresent(td -> td.setName(newName.trim() + " 팀원"));

        // 팀 채팅방 이름도 업데이트
        chatRoomService.findTeamChat(clubId, teamId)
                .ifPresent(room -> room.setName(newName.trim() + " 채팅"));

        log.info("팀명 변경: {} → {}", oldName, newName.trim());
    }

    // ========== 팀 해체 (soft delete) ==========

    @Transactional
    public void dissolveTeam(Long clubId, Long teamId, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        Team team = getTeamOrThrow(teamId, clubId);

        // 시스템 팀은 해체 불가 (데이터 변경 전에 체크)
        if (team.isSystemTeam()) {
            throw new IllegalArgumentException("시스템 팀은 해체할 수 없습니다");
        }

        // 팀 태그 전부 삭제
        memberTagRepository.deleteAllByTeamId(teamId);

        // 팀 태그 정의도 삭제
        tagDefinitionRepository.deleteAllByTeamId(teamId);

        // soft delete (post_teams/drive_file_teams는 팀 soft delete 후에도 FK 유효)
        team.setDeleted(true);

        log.info("팀 해체: clubId={}, teamId={}, name={}", clubId, teamId, team.getName());
    }

    // ========== 팀원 관리 ==========

    @Transactional
    public TeamDetailResponse addMembers(Long clubId, Long teamId, List<Long> clubMembershipIds, Long userId) {
        authService.validateTeamManager(userId, clubId, teamId);

        Team team = getTeamOrThrow(teamId, clubId);
        Club club = team.getClub();

        for (Long cmId : clubMembershipIds) {
            // 이미 팀에 있으면 건너뛰기
            if (memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(cmId, teamId, TagType.TEAM_MEMBER)
                || memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(cmId, teamId, TagType.TEAM_LEADER)) {
                continue;
            }
            ClubMembership cm = getApprovedMember(cmId, clubId);
            addTeamTag(cm, club, team, TagType.TEAM_MEMBER, PermissionLevel.REGULAR);

            // 팀 채팅방에 자동 참여
            ChatRoom teamChat = chatRoomService.ensureTeamChat(club, team);
            chatMemberService.addMemberToRoom(teamChat, cm);

            // 알림: 팀 배정
            eventPublisher.publishEvent(NotificationEvent.of(
                    clubId, cm.getUser().getId(), NotificationType.TEAM_CHANGE,
                    team.getName() + " 팀에 배정되었습니다",
                    cm.getName() + "님이 " + team.getName() + " 팀에 배정되었습니다",
                    "/club/teams/" + teamId
            ));
        }

        return getTeam(clubId, teamId, userId);
    }

    @Transactional
    public void removeMember(Long clubId, Long teamId, Long tagId, Long userId) {
        authService.validateTeamManager(userId, clubId, teamId);

        MemberTag tag = memberTagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("태그를 찾을 수 없습니다: " + tagId));

        if (tag.getTeam() == null || !tag.getTeam().getId().equals(teamId)) {
            throw new IllegalArgumentException("해당 팀의 태그가 아닙니다");
        }

        String memberName = tag.getClubMembership().getName();
        Long targetUserId = tag.getClubMembership().getUser().getId();
        String teamName = tag.getTeam().getName();

        log.info("팀원 제거: teamId={}, memberName={}", teamId, memberName);
        memberTagRepository.delete(tag);

        // 팀 채팅방에서 제거
        Team team = tag.getTeam();
        chatRoomService.findTeamChat(team.getClub().getId(), teamId)
                .ifPresent(room -> chatMemberService.removeMemberFromRoom(room.getId(), tag.getClubMembership().getId()));

        // 팀에 1명만 남으면 자동 팀장 승격
        autoPromoteSoleLeader(teamId);

        // 알림: 팀 해제
        eventPublisher.publishEvent(NotificationEvent.of(
                clubId, targetUserId, NotificationType.TEAM_CHANGE,
                teamName + " 팀에서 제외되었습니다",
                memberName + "님이 " + teamName + " 팀에서 제외되었습니다",
                "/club/teams/" + teamId
        ));
    }

    @Transactional
    public TeamDetailResponse removeTeamLeader(Long clubId, Long teamId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Team team = getTeamOrThrow(teamId, clubId);

        // 팀장 → 팀원으로 전환
        memberTagRepository.findByTeamIdAndTagType(teamId, TagType.TEAM_LEADER)
                .forEach(tag -> {
                    tag.setTagType(TagType.TEAM_MEMBER);
                    tag.setName(team.getName() + " 팀원");
                    tag.setPermissionLevel(PermissionLevel.REGULAR);
                });

        log.info("팀장 해제: clubId={}, teamId={}", clubId, teamId);
        return getTeam(clubId, teamId, userId);
    }

    @Transactional
    public TeamDetailResponse changeTeamLeader(Long clubId, Long teamId, Long clubMembershipId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Team team = getTeamOrThrow(teamId, clubId);
        Club club = team.getClub();

        // 기존 팀장 → 팀원으로 전환
        memberTagRepository.findByTeamIdAndTagType(teamId, TagType.TEAM_LEADER)
                .forEach(tag -> {
                    tag.setTagType(TagType.TEAM_MEMBER);
                    tag.setName(team.getName() + " 팀원");
                    tag.setPermissionLevel(PermissionLevel.REGULAR);
                });

        // 새 팀장: 기존 팀원 태그가 있으면 업그레이드, 없으면 새로 생성
        var existingTag = memberTagRepository
                .findByClubMembershipIdAndTeamIdAndTagType(clubMembershipId, teamId, TagType.TEAM_MEMBER);

        if (existingTag.isPresent()) {
            MemberTag tag = existingTag.get();
            tag.setTagType(TagType.TEAM_LEADER);
            tag.setName(team.getName() + " 팀장");
            tag.setPermissionLevel(PermissionLevel.REGULAR);
        } else {
            ClubMembership cm = getApprovedMember(clubMembershipId, clubId);
            addTeamTag(cm, club, team, TagType.TEAM_LEADER, PermissionLevel.REGULAR);
        }

        // 알림: 팀장 변경
        ClubMembership newLeader = clubMembershipRepository.findById(clubMembershipId).orElse(null);
        if (newLeader != null) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    clubId, newLeader.getUser().getId(), NotificationType.ROLE_CHANGE,
                    team.getName() + " 팀장으로 임명되었습니다",
                    newLeader.getName() + "님이 " + team.getName() + " 팀장으로 임명되었습니다",
                    "/club/teams/" + teamId
            ));
        }

        return getTeam(clubId, teamId, userId);
    }

    @Transactional(readOnly = true)
    public List<ClubMemberResponse> getUnassignedMembers(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        return memberTagRepository.findUnassignedMembers(clubId).stream()
                .map(cm -> {
                    List<MemberTag> tags = memberTagRepository.findByClubMembershipId(cm.getId());
                    return ClubMemberResponse.from(cm, tags);
                })
                .toList();
    }

    // ========== Private ==========

    /** 팀에 1명만 남고 팀장이 없으면 자동 팀장 승격 */
    private void autoPromoteSoleLeader(Long teamId) {
        List<MemberTag> remaining = memberTagRepository.findByTeamId(teamId);
        if (remaining.size() != 1) return;

        MemberTag sole = remaining.get(0);
        if (sole.getTagType() == TagType.TEAM_LEADER) return; // 이미 팀장

        // 팀장이 없는지 확인
        boolean hasLeader = remaining.stream().anyMatch(t -> t.getTagType() == TagType.TEAM_LEADER);
        if (hasLeader) return;

        sole.setTagType(TagType.TEAM_LEADER);
        sole.setName(sole.getTeam().getName() + " 팀장");
        log.info("자동 팀장 승격: teamId={}, member={}", teamId, sole.getClubMembership().getName());
    }

    private Team getTeamOrThrow(Long teamId, Long clubId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        if (!team.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 팀이 아닙니다");
        }
        if (team.isDeleted()) {
            throw new IllegalArgumentException("해체된 팀입니다: " + teamId);
        }
        return team;
    }

    private ClubMembership getApprovedMember(Long clubMembershipId, Long clubId) {
        ClubMembership cm = clubMembershipRepository.findById(clubMembershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다: " + clubMembershipId));
        if (!cm.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 멤버가 아닙니다");
        }
        if (cm.getStatus() != MemberStatus.APPROVED) {
            throw new IllegalArgumentException("승인된 멤버만 팀에 추가할 수 있습니다");
        }
        return cm;
    }

    private void addTeamTag(ClubMembership cm, Club club, Team team, TagType tagType, PermissionLevel level) {
        String suffix = switch (tagType) {
            case TEAM_LEADER -> " 팀장";
            case TEAM_MANAGER -> " 매니저";
            default -> " 팀원";
        };
        int order = switch (tagType) {
            case TEAM_LEADER -> 10;
            case TEAM_MANAGER -> 15;
            default -> 20;
        };
        String tagName = team.getName() + suffix;
        memberTagRepository.save(MemberTag.builder()
                .clubMembership(cm)
                .club(club)
                .name(tagName)
                .tagType(tagType)
                .permissionLevel(level)
                .team(team)
                .displayOrder(order)
                .build());
        log.info("태그 부여: member={}, tag={}", cm.getName(), tagName);
    }

    private String resolveUniqueTeamName(Long clubId, String baseName) {
        // 활성 팀 간 중복만 방지 (해체된 팀과 같은 이름 재생성 허용 — OF-038-1)
        List<String> existingNames = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId)
                .stream().map(Team::getName).toList();
        if (!existingNames.contains(baseName)) return baseName;
        for (int i = 1; i <= 99; i++) {
            String candidate = baseName + " " + i;
            if (!existingNames.contains(candidate)) return candidate;
        }
        return baseName + " " + System.currentTimeMillis();
    }

    // ========== 매니저 임명/해제 ==========

    /**
     * 팀원을 매니저로 임명 (팀장만 가능)
     */
    @Transactional
    public void appointManager(Long clubId, Long teamId, Long membershipId, Long userId) {
        authService.validateClubMember(userId, clubId);
        Team team = getTeamOrThrow(teamId, clubId);
        validateTeamLeader(userId, teamId, clubId);

        // 이미 매니저인지 확인
        if (memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(membershipId, teamId, TagType.TEAM_MANAGER)) {
            throw new IllegalArgumentException("이미 매니저입니다");
        }

        // TEAM_MEMBER 태그를 TEAM_MANAGER로 변경
        MemberTag memberTag = memberTagRepository
                .findByClubMembershipIdAndTeamIdAndTagType(membershipId, teamId, TagType.TEAM_MEMBER)
                .orElseThrow(() -> new IllegalArgumentException("해당 팀의 팀원이 아닙니다"));

        memberTag.setTagType(TagType.TEAM_MANAGER);
        memberTag.setName(team.getName() + " 매니저");
        memberTag.setDisplayOrder(15);
        // 매니저는 REGULAR 유지 (EXECUTIVE가 아님)
        log.info("매니저 임명: team={}, member={}", team.getName(), memberTag.getClubMembership().getName());

        // 알림: 매니저 임명
        eventPublisher.publishEvent(NotificationEvent.of(
                clubId, memberTag.getClubMembership().getUser().getId(), NotificationType.ROLE_CHANGE,
                team.getName() + " 매니저로 임명되었습니다",
                memberTag.getClubMembership().getName() + "님이 " + team.getName() + " 매니저로 임명되었습니다",
                "/club/teams/" + teamId
        ));
    }

    /**
     * 매니저를 팀원으로 강등 (팀장만 가능)
     */
    @Transactional
    public void revokeManager(Long clubId, Long teamId, Long membershipId, Long userId) {
        authService.validateClubMember(userId, clubId);
        Team team = getTeamOrThrow(teamId, clubId);
        validateTeamLeader(userId, teamId, clubId);

        MemberTag managerTag = memberTagRepository
                .findByClubMembershipIdAndTeamIdAndTagType(membershipId, teamId, TagType.TEAM_MANAGER)
                .orElseThrow(() -> new IllegalArgumentException("해당 팀의 매니저가 아닙니다"));

        managerTag.setTagType(TagType.TEAM_MEMBER);
        managerTag.setName(team.getName() + " 팀원");
        managerTag.setDisplayOrder(20);
        log.info("매니저 해제: team={}, member={}", team.getName(), managerTag.getClubMembership().getName());

        // 알림: 매니저 해제
        eventPublisher.publishEvent(NotificationEvent.of(
                clubId, managerTag.getClubMembership().getUser().getId(), NotificationType.ROLE_CHANGE,
                team.getName() + " 매니저에서 해제되었습니다",
                managerTag.getClubMembership().getName() + "님이 " + team.getName() + " 매니저에서 해제되었습니다",
                "/club/teams/" + teamId
        ));
    }

    /** SEC-7: clubId 스코핑 — 타 동아리 팀장 권한 통과 방지 */
    private void validateTeamLeader(Long userId, Long teamId, Long clubId) {
        ClubMembership membership = clubMembershipRepository.findByUserIdAndClubId(userId, clubId)
                .orElseThrow(() -> new SecurityException("멤버십 없음"));
        boolean isLeader = memberTagRepository.existsByClubMembershipIdAndTeamIdAndTagType(
                membership.getId(), teamId, TagType.TEAM_LEADER);
        if (!isLeader) throw new SecurityException("팀장만 매니저를 임명/해제할 수 있습니다");
    }

    private String findLeaderName(Long teamId) {
        List<MemberTag> leaders = memberTagRepository.findByTeamIdAndTagType(teamId, TagType.TEAM_LEADER);
        return leaders.isEmpty() ? null : leaders.get(0).getClubMembership().getName();
    }

}
