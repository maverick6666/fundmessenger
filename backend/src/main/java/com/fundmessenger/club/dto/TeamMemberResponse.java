package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.MemberTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class TeamMemberResponse {
    private Long tagId;
    private Long clubMembershipId;
    private String name;
    private String studentId;
    private String teamRole;  // TEAM_LEADER or TEAM_MEMBER
    private LocalDateTime joinedAt;

    public static TeamMemberResponse from(MemberTag tag) {
        return TeamMemberResponse.builder()
                .tagId(tag.getId())
                .clubMembershipId(tag.getClubMembership().getId())
                .name(tag.getClubMembership().getName())
                .studentId(tag.getClubMembership().getStudentId())
                .teamRole(tag.getTagType().name().replace("TEAM_", ""))
                .joinedAt(tag.getCreatedAt())
                .build();
    }
}
