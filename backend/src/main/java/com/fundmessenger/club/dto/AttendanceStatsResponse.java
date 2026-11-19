package com.fundmessenger.club.dto;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AttendanceStatsResponse {

    private double weeklyRate;
    private double monthlyRate;
    private List<MemberStat> memberStats;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class MemberStat {
        private String memberName;
        private int accessDays;
        private int totalDays;
    }
}
