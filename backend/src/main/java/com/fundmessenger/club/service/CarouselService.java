package com.fundmessenger.club.service;

import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.home.dto.ExecutiveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarouselService {

    private final CarouselMemberRepository carouselMemberRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final ClubAuthorizationService authService;

    @Transactional(readOnly = true)
    public List<ExecutiveResponse> getCarouselMembers(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        return carouselMemberRepository.findByClubIdWithMembershipDetails(clubId).stream()
                .map(ExecutiveResponse::fromCarousel)
                .toList();
    }

    @Transactional
    public ExecutiveResponse addCarouselMember(Long clubId, Long membershipId, String roleName, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        if (carouselMemberRepository.existsByClubIdAndClubMembershipId(clubId, membershipId)) {
            throw new IllegalArgumentException("이미 회장단에 등록된 멤버입니다");
        }

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));
        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다"));

        int maxOrder = carouselMemberRepository.findByClubIdOrderByDisplayOrderAsc(clubId).stream()
                .mapToInt(CarouselMember::getDisplayOrder)
                .max().orElse(-1);

        CarouselMember cm = carouselMemberRepository.save(CarouselMember.builder()
                .club(club)
                .clubMembership(membership)
                .roleName(roleName != null ? roleName.trim() : "멤버")
                .displayOrder(maxOrder + 1)
                .build());

        // 회장단 시스템 팀 동기화
        ensureSystemTeamMember(clubId, club, membership);

        log.info("캐러셀 멤버 추가: clubId={}, membershipId={}, role={}", clubId, membershipId, roleName);
        return ExecutiveResponse.fromCarousel(cm);
    }

    @Transactional
    public ExecutiveResponse updateCarouselMember(Long clubId, Long carouselId, String roleName, String bio, Integer displayOrder, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        CarouselMember cm = carouselMemberRepository.findById(carouselId)
                .orElseThrow(() -> new IllegalArgumentException("캐러셀 멤버를 찾을 수 없습니다"));

        if (roleName != null) cm.setRoleName(roleName.trim());
        if (bio != null) cm.setBio(bio.trim());
        if (displayOrder != null) cm.setDisplayOrder(displayOrder);

        return ExecutiveResponse.fromCarousel(cm);
    }

    @Transactional
    public void removeCarouselMember(Long clubId, Long carouselId, boolean removeExecutive, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        CarouselMember cm = carouselMemberRepository.findById(carouselId)
                .orElseThrow(() -> new IllegalArgumentException("캐러셀 멤버를 찾을 수 없습니다"));

        Long membershipId = cm.getClubMembership().getId();

        // 회장단 시스템 팀에서도 제거
        removeSystemTeamMember(clubId, membershipId);

        carouselMemberRepository.delete(cm);

        if (removeExecutive) {
            ClubMembership membership = cm.getClubMembership();
            membership.setExecutive(false);
            log.info("캐러셀 제거 + EXECUTIVE 해제: clubId={}, membershipId={}", clubId, membershipId);
        } else {
            log.info("캐러셀 제거 (EXECUTIVE 유지): clubId={}, membershipId={}", clubId, membershipId);
        }
    }

    @Transactional
    public ExecutiveResponse updateCarouselBioByMember(Long clubId, Long carouselId, String bio, Long userId) {
        CarouselMember cm = carouselMemberRepository.findById(carouselId)
                .orElseThrow(() -> new IllegalArgumentException("캐러셀 멤버를 찾을 수 없습니다"));

        // 본인 확인
        if (!cm.getClubMembership().getUser().getId().equals(userId)) {
            throw new SecurityException("본인의 캐러셀 소개글만 수정할 수 있습니다");
        }

        cm.setBio(bio != null ? bio.trim() : null);
        return ExecutiveResponse.fromCarousel(cm);
    }

    @Transactional
    public void toggleExecutive(Long clubId, Long membershipId, boolean executive, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        ClubMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다"));
        if (!membership.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 멤버가 아닙니다");
        }

        membership.setExecutive(executive);
        log.info("EXECUTIVE 토글: clubId={}, membershipId={}, executive={}", clubId, membershipId, executive);
    }

    // ========== 회장단 시스템 팀 동기화 ==========

    private void ensureSystemTeamMember(Long clubId, Club club, ClubMembership membership) {
        Team systemTeam = teamRepository.findByClubIdAndSystemTeamTrue(clubId)
                .orElseGet(() -> {
                    Team team = teamRepository.save(Team.builder()
                            .name("회장단")
                            .club(club)
                            .systemTeam(true)
                            .build());

                    // 팀 태그 정의 생성
                    tagDefinitionRepository.save(TagDefinition.builder()
                            .club(club).name("회장단 팀장").team(team)
                            .tagType(TagType.TEAM_LEADER).permissionLevel(PermissionLevel.REGULAR)
                            .displayOrder(10).build());
                    tagDefinitionRepository.save(TagDefinition.builder()
                            .club(club).name("회장단 팀원").team(team)
                            .tagType(TagType.TEAM_MEMBER).permissionLevel(PermissionLevel.REGULAR)
                            .displayOrder(20).build());

                    log.info("회장단 시스템 팀 자동 생성: clubId={}, teamId={}", clubId, team.getId());
                    return team;
                });

        // 이미 팀에 있으면 스킵
        if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), systemTeam.getId())) {
            memberTagRepository.save(MemberTag.builder()
                    .clubMembership(membership)
                    .club(club)
                    .name("회장단 팀원")
                    .tagType(TagType.TEAM_MEMBER)
                    .permissionLevel(PermissionLevel.REGULAR)
                    .team(systemTeam)
                    .displayOrder(20)
                    .build());
        }
    }

    private void removeSystemTeamMember(Long clubId, Long membershipId) {
        teamRepository.findByClubIdAndSystemTeamTrue(clubId).ifPresent(systemTeam -> {
            memberTagRepository.findByClubMembershipIdAndTeamId(membershipId, systemTeam.getId())
                    .ifPresent(memberTagRepository::delete);
        });
    }
}
