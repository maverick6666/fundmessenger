package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.ClubMembership;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class JoinRequestResponse {
    private Long membershipId;
    private Long userId;
    private String name;
    private String studentId;
    private String phone;
    private LocalDateTime requestedAt;

    public static JoinRequestResponse from(ClubMembership cm) {
        return JoinRequestResponse.builder()
                .membershipId(cm.getId())
                .userId(cm.getUser().getId())
                .name(cm.getName())
                .studentId(cm.getStudentId())
                .phone(cm.getPhone())
                .requestedAt(cm.getAppliedAt())
                .build();
    }
}
