package com.fundmessenger.club.service;

import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.dto.*;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.drive.repository.DriveFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubMemberService {

    private final ClubMembershipRepository membershipRepository;
    private final MemberTagRepository memberTagRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final TeamRepository teamRepository;
    private final ClubSettingsRepository settingsRepository;
    private final ClubAuthorizationService authService;
    private final PostRepository postRepository;
    private final DriveFileRepository driveFileRepository;
    private final ChatRoomService chatRoomService;
    private final ChatMemberService chatMemberService;

    @Transactional(readOnly = true)
    public MyClubInfoResponse getMyClubInfo(Long userId) {
        List<ClubMembership> memberships = membershipRepository.findByUserIdAndStatus(userId, MemberStatus.APPROVED);
        if (memberships.isEmpty()) {
            throw new IllegalStateException("승인된 동아리 멤버십이 없습니다");
        }

        ClubMembership membership = memberships.get(0);
        Long clubId = membership.getClub().getId();

        long totalMembers = membershipRepository
                .findByClubIdAndStatus(clubId, MemberStatus.APPROVED).stream()
                .filter(m -> !m.isBot()).count();
        long totalTeams = teamRepository.countByClubIdAndDeletedFalse(clubId);

        // 내 태그 목록
        List<MemberTag> myTags = memberTagRepository.findByClubMembershipId(membership.getId());
        List<MyClubInfoResponse.MyTagSummary> tagSummaries = myTags.stream()
                .map(mt -> new MyClubInfoResponse.MyTagSummary(
                        mt.getId(),
                        mt.getName(),
                        mt.getTagType().name(),
                        mt.getPermissionLevel().name(),
                        mt.getTeam() != null ? mt.getTeam().getId() : null,
                        mt.getTeam() != null ? mt.getTeam().getName() : null,
                        mt.getTeam() != null && mt.getTeam().getTeamType() != null ? mt.getTeam().getTeamType().name() : null,
                        mt.getTeam() != null && mt.getTeam().isSystemTeam()
                ))
                .toList();

        boolean isExecutive = membership.isExecutive();

        String clubLogoUrl = settingsRepository.findById(clubId)
                .map(ClubSettings::getClubLogoUrl)
                .orElse(null);

        long myPostCount = postRepository.countByAuthorId(membership.getId());
        long myFileCount = driveFileRepository.countByClubIdAndUploaderId(clubId, membership.getId());

        return MyClubInfoResponse.builder()
                .clubId(clubId)
                .clubName(membership.getClub().getName())
                .clubLogoUrl(clubLogoUrl)
                .schoolName(membership.getClub().getSchool().getName())
                .membershipId(membership.getId())
                .myName(membership.getName())
                .executive(isExecutive)
                .totalMembers(totalMembers)
                .totalTeams(totalTeams)
                .myPostCount(myPostCount)
                .myFileCount(myFileCount)
                .myTags(tagSummaries)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClubMemberResponse> getMembers(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        List<ClubMembership> members = membershipRepository
                .findByClubIdAndStatus(clubId, MemberStatus.APPROVED);

        List<ClubMembership> nonBotMembers = members.stream()
                .filter(cm -> !cm.isBot())
                .toList();

        // 배치 태그 조회 (N+1 방지: 멤버 수만큼 쿼리 → 1개 쿼리)
        List<Long> membershipIds = nonBotMembers.stream()
                .map(ClubMembership::getId)
                .toList();
        Map<Long, List<MemberTag>> tagsByMembership = membershipIds.isEmpty()
                ? Collections.emptyMap()
                : memberTagRepository.findByClubMembershipIdIn(membershipIds).stream()
                        .collect(Collectors.groupingBy(mt -> mt.getClubMembership().getId()));

        return nonBotMembers.stream()
                .map(cm -> ClubMemberResponse.from(cm,
                        tagsByMembership.getOrDefault(cm.getId(), List.of())))
                .toList();
    }

    // ========== 가입 요청 관리 ==========

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getJoinRequests(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        return membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.PENDING)
                .stream()
                .map(JoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public JoinRequestResponse approveJoinRequest(Long clubId, Long membershipId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("가입 요청을 찾을 수 없습니다: " + membershipId));

        if (!target.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 가입 요청이 아닙니다");
        }
        if (target.getStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException("대기 중인 요청만 승인할 수 있습니다");
        }

        target.setStatus(MemberStatus.APPROVED);
        target.setApprovedAt(LocalDateTime.now());

        // 일반부원 태그 자동 부여
        memberTagRepository.save(MemberTag.builder()
                .clubMembership(target)
                .club(target.getClub())
                .name("일반부원")
                .tagType(TagType.SYSTEM)
                .permissionLevel(PermissionLevel.REGULAR)
                .displayOrder(100)
                .build());

        // 동아리 채팅방에 자동 참여
        ChatRoom clubChat = chatRoomService.ensureClubChat(target.getClub());
        chatMemberService.addMemberToRoom(clubChat, target);

        return JoinRequestResponse.from(target);
    }

    @Transactional
    public JoinRequestResponse rejectJoinRequest(Long clubId, Long membershipId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("가입 요청을 찾을 수 없습니다: " + membershipId));

        if (!target.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 가입 요청이 아닙니다");
        }
        if (target.getStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException("대기 중인 요청만 거절할 수 있습니다");
        }

        target.setStatus(MemberStatus.REJECTED);
        return JoinRequestResponse.from(target);
    }

    /** 멤버에게 태그 부여 (TagDefinition 기반) */
    @Transactional
    public ClubMemberResponse assignTag(Long clubId, Long membershipId, Long tagDefinitionId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다: " + membershipId));

        if (!target.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 멤버가 아닙니다");
        }

        TagDefinition td = tagDefinitionRepository.findById(tagDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("태그 정의를 찾을 수 없습니다: " + tagDefinitionId));

        // 이미 동일 이름 태그가 있으면 건너뛰기
        if (memberTagRepository.findByClubMembershipIdAndName(membershipId, td.getName()).isPresent()) {
            List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membershipId);
            return ClubMemberResponse.from(target, tags);
        }

        memberTagRepository.save(MemberTag.builder()
                .clubMembership(target)
                .club(target.getClub())
                .name(td.getName())
                .tagType(td.getTagType())
                .permissionLevel(td.getPermissionLevel())
                .team(td.getTeam())
                .displayOrder(td.getDisplayOrder())
                .build());

        List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membershipId);
        return ClubMemberResponse.from(target, tags);
    }

    // ========== 태그 정의 관리 (권한관리 탭) ==========

    @Transactional(readOnly = true)
    public List<TagDefinitionResponse> getTagDefinitions(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        return tagDefinitionRepository.findByClubIdOrderByDisplayOrderAsc(clubId).stream()
                .map(td -> {
                    long count = memberTagRepository.countByClubIdAndName(clubId, td.getName());
                    return TagDefinitionResponse.from(td, count);
                })
                .toList();
    }

    @Transactional
    public TagDefinitionResponse createCustomTag(Long clubId, String name, String permissionLevel, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("태그 이름은 필수입니다");
        }

        String trimmed = name.trim();
        if (tagDefinitionRepository.existsByClubIdAndName(clubId, trimmed)) {
            throw new IllegalArgumentException("이미 같은 이름의 태그가 존재합니다: " + trimmed);
        }

        Club club = membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"))
                .getClub();

        PermissionLevel level = "EXECUTIVE".equals(permissionLevel)
                ? PermissionLevel.EXECUTIVE : PermissionLevel.REGULAR;

        TagDefinition td = tagDefinitionRepository.save(TagDefinition.builder()
                .club(club)
                .name(trimmed)
                .tagType(TagType.CUSTOM)
                .permissionLevel(level)
                .displayOrder(50)
                .build());

        return TagDefinitionResponse.from(td, 0);
    }

    @Transactional
    public TagDefinitionResponse updateTagDefinition(Long clubId, Long tagId, String name, String permissionLevel, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        TagDefinition td = tagDefinitionRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("태그 정의를 찾을 수 없습니다: " + tagId));

        if (!td.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 태그가 아닙니다");
        }

        String oldName = td.getName();

        // 이름 변경 (CUSTOM만 가능)
        if (name != null && !name.trim().isEmpty() && td.getTagType() == TagType.CUSTOM) {
            String trimmed = name.trim();
            if (!trimmed.equals(oldName) && tagDefinitionRepository.existsByClubIdAndName(clubId, trimmed)) {
                throw new IllegalArgumentException("이미 같은 이름의 태그가 존재합니다: " + trimmed);
            }
            td.setName(trimmed);
        }

        // 권한 레벨 변경 (모든 타입 가능)
        if (permissionLevel != null) {
            PermissionLevel level = "EXECUTIVE".equals(permissionLevel)
                    ? PermissionLevel.EXECUTIVE : PermissionLevel.REGULAR;
            td.setPermissionLevel(level);

            // 해당 이름의 모든 MemberTag도 함께 업데이트
            List<MemberTag> memberTags = memberTagRepository.findByClubIdAndName(clubId, oldName);
            for (MemberTag mt : memberTags) {
                mt.setPermissionLevel(level);
                if (!oldName.equals(td.getName())) {
                    mt.setName(td.getName());
                }
            }
        }

        long count = memberTagRepository.countByClubIdAndName(clubId, td.getName());
        return TagDefinitionResponse.from(td, count);
    }

    @Transactional
    public void deleteTagDefinition(Long clubId, Long tagId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        TagDefinition td = tagDefinitionRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("태그 정의를 찾을 수 없습니다: " + tagId));

        if (!td.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 태그가 아닙니다");
        }

        if (td.getTagType() != TagType.CUSTOM) {
            throw new IllegalArgumentException("CUSTOM 태그만 삭제할 수 있습니다");
        }

        // 해당 이름의 모든 MemberTag 삭제
        List<MemberTag> memberTags = memberTagRepository.findByClubIdAndName(clubId, td.getName());
        memberTagRepository.deleteAll(memberTags);

        tagDefinitionRepository.delete(td);
    }

    // ========== 멤버 비활성화/삭제 ==========

    /** 멤버 비활성화 (APPROVED → INACTIVE, 모든 태그 제거) */
    @Transactional
    public void deactivateMember(Long clubId, Long membershipId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다"));
        if (!membership.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 멤버가 아닙니다");
        }
        if (membership.getStatus() != MemberStatus.APPROVED) {
            throw new IllegalArgumentException("활성 멤버만 비활성화할 수 있습니다");
        }
        // 자기 자신 비활성화 방지
        if (membership.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("자기 자신은 비활성화할 수 없습니다");
        }

        // 모든 태그 제거
        List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membershipId);
        memberTagRepository.deleteAll(tags);

        membership.setStatus(MemberStatus.INACTIVE);
        membershipRepository.save(membership);
    }

    /** 멤버 삭제 (membership + 모든 태그 hard delete) */
    @Transactional
    public void deleteMember(Long clubId, Long membershipId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다"));
        if (!membership.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 멤버가 아닙니다");
        }
        // 자기 자신 삭제 방지
        if (membership.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("자기 자신은 삭제할 수 없습니다");
        }

        // 모든 태그 제거
        List<MemberTag> tags = memberTagRepository.findByClubMembershipId(membershipId);
        memberTagRepository.deleteAll(tags);

        membershipRepository.delete(membership);
    }
}
