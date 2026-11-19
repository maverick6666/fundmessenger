package com.fundmessenger.club.dto;

import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.club.entity.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class TeamResponse {
    private Long id;
    private String name;
    private String teamType;
    private long memberCount;
    private String leaderName;
    private Integer creditAllocation;
    private List<GenerationInfo> generations;
    private LocalDateTime createdAt;

    @Getter
    @AllArgsConstructor
    public static class GenerationInfo {
        private Long id;
        private String name;
    }

    public static TeamResponse from(Team team, long memberCount, String leaderName) {
        List<GenerationInfo> gens = team.getGenerations().stream()
                .sorted(Comparator.comparingInt(SemiCategory::getDisplayOrder))
                .map(g -> new GenerationInfo(g.getId(), g.getName()))
                .toList();

        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .teamType(team.getTeamType() != null ? team.getTeamType().name() : "CUSTOM")
                .memberCount(memberCount)
                .leaderName(leaderName)
                .creditAllocation(team.getCreditAllocation())
                .generations(gens)
                .createdAt(team.getCreatedAt())
                .build();
    }
}
