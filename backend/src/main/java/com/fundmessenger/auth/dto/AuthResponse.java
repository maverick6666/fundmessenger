package com.fundmessenger.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
    private String userStatus;  // "NO_CLUB" | "PENDING" | "APPROVED"
    private boolean superAdmin;

    // PENDING 상태일 때만 채워지는 필드
    private String pendingSchoolName;
    private String pendingClubName;
    private String pendingAppliedAt;  // ISO 날짜 문자열
}
