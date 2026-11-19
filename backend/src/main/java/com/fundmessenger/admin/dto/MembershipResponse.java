package com.fundmessenger.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.PermissionLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MembershipResponse {
    private Long id;
    private String userName;
    private String studentId;
    private String phone;
    private String clubName;
    private String schoolName;
    @JsonProperty("isExecutive")
    private boolean executive;
    private List<String> tagNames;
    private String status;
    private LocalDateTime appliedAt;

    public static MembershipResponse from(ClubMembership m) {
        return MembershipResponse.builder()
                .id(m.getId())
                .userName(m.getName())
                .studentId(m.getStudentId())
                .phone(m.getPhone())
                .clubName(m.getClub().getName())
                .schoolName(m.getClub().getSchool().getName())
                .status(m.getStatus().name())
                .appliedAt(m.getAppliedAt())
                .build();
    }

    public static MembershipResponse from(ClubMembership m, List<MemberTag> tags) {
        boolean executive = m.isExecutive();
        List<String> names = tags.stream().map(MemberTag::getName).toList();

        return MembershipResponse.builder()
                .id(m.getId())
                .userName(m.getName())
                .studentId(m.getStudentId())
                .phone(m.getPhone())
                .clubName(m.getClub().getName())
                .schoolName(m.getClub().getSchool().getName())
                .executive(executive)
                .tagNames(names)
                .status(m.getStatus().name())
                .appliedAt(m.getAppliedAt())
                .build();
    }
}
