package com.fundmessenger.club.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTeamRequest {
    /** 팀 이름 (미입력 시 기본명 "새 팀") */
    @Size(max = 50, message = "팀 이름은 50자 이내여야 합니다")
    private String name;

    /** 팀장으로 지정할 멤버십 ID (선택 — 빈 팀 허용) */
    private Long leaderMembershipId;

    /** 팀 한줄 소개 (선택) */
    @Size(max = 200, message = "팀 소개는 200자 이내여야 합니다")
    private String description;

    /** 팀 유형 (FUND, ANALYSIS, REAL_ESTATE, CUSTOM). 미입력 시 CUSTOM */
    private String teamType;
}
