package com.fundmessenger.club.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.entity.PermissionLevel;
import com.fundmessenger.club.entity.TagType;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClubAuthorizationService {

    private final ClubMembershipRepository membershipRepository;
    private final MemberTagRepository memberTagRepository;

    /** 동아리 승인 멤버인지 확인 (APPROVED + ALUMNI). 아니면 SecurityException */
    public ClubMembership validateClubMember(Long userId, Long clubId) {
        return membershipRepository.findByUserIdAndClubId(userId, clubId)
                .filter(m -> m.getStatus() == MemberStatus.APPROVED || m.getStatus() == MemberStatus.ALUMNI)
                .orElseThrow(() -> new SecurityException("동아리 멤버가 아닙니다"));
    }

    /** 활성 멤버(APPROVED)만 확인 — 글쓰기 등 능동 행위 */
    public ClubMembership validateActiveMember(Long userId, Long clubId) {
        return membershipRepository.findByUserIdAndClubId(userId, clubId)
                .filter(m -> m.getStatus() == MemberStatus.APPROVED)
                .orElseThrow(() -> new SecurityException("활성 멤버가 아닙니다"));
    }

    /** 임원급(EXECUTIVE) 권한 확인 — 관리 탭 접근 */
    public ClubMembership validateClubAdmin(Long userId, Long clubId) {
        ClubMembership membership = validateClubMember(userId, clubId);
        if (!isExecutive(membership)) {
            throw new SecurityException("임원급 권한이 필요합니다");
        }
        return membership;
    }

    /** 임원급 또는 해당 팀의 LEADER인지 확인 */
    public ClubMembership validateTeamManager(Long userId, Long clubId, Long teamId) {
        ClubMembership membership = validateClubMember(userId, clubId);

        // 임원급은 모든 팀 관리 가능
        if (isExecutive(membership)) {
            return membership;
        }

        // 해당 팀의 TEAM_LEADER 또는 TEAM_MANAGER 태그 보유 여부
        boolean isLeaderOrManager = memberTagRepository
                .existsByClubMembershipIdAndTeamIdAndTagType(
                        membership.getId(), teamId, TagType.TEAM_LEADER)
                || memberTagRepository
                .existsByClubMembershipIdAndTeamIdAndTagType(
                        membership.getId(), teamId, TagType.TEAM_MANAGER);

        if (!isLeaderOrManager) {
            throw new SecurityException("팀 관리 권한이 없습니다");
        }
        return membership;
    }

    /** 멤버의 임원급 권한 여부 (ClubMembership.executive 직접 필드) */
    public boolean isExecutive(ClubMembership membership) {
        return membership.isExecutive();
    }
}
