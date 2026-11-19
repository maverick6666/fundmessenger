package com.fundmessenger.club.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GenerationTransitionRequest {

    /** 새 기수 이름 (예: "36기"). null이면 자동 생성 */
    private String newGenerationName;

    /** 계속 활동 멤버 ID 목록 */
    private List<Long> continueMemberIds;

    /** 졸업 처리 멤버 ID 목록 */
    private List<Long> alumniMemberIds;

    /** 탈퇴 처리 멤버 ID 목록 */
    private List<Long> inactiveMemberIds;

    /** 유지할 팀 ID 목록 (기수 태그 추가) */
    private List<Long> retainTeamIds;

    /** 해체할 팀 ID 목록 (soft delete) */
    private List<Long> dissolveTeamIds;

    /** 차기 회장 멤버십 ID */
    private Long nextPresidentMembershipId;
}
