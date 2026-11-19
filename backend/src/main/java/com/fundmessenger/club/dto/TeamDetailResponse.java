package com.fundmessenger.club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class TeamDetailResponse {
    private Long id;
    private String name;
    private String teamType;
    private Integer creditAllocation;
    private String description;
    private LocalDateTime createdAt;
    private List<TeamMemberResponse> members;
    private List<String> generations;  // 기수 이름 목록
}
