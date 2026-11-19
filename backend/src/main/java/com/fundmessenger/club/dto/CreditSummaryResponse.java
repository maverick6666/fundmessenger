package com.fundmessenger.club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class CreditSummaryResponse {
    private Integer clubBalance;        // 동아리 미분배 잔액
    private Integer totalAllocated;     // 팀에 분배된 총액
    private Integer totalBalance;       // clubBalance + totalAllocated
    private List<TeamCreditInfo> teams;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class TeamCreditInfo {
        private Long teamId;
        private String teamName;
        private Integer creditAllocation;
        private long memberCount;
    }
}
