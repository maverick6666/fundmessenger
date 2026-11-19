package com.fundmessenger.club.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class ClubMemberResponse {
    private Long membershipId;
    private Long userId;
    private String name;
    private String studentId;
    private String permissionLevel;
    @JsonProperty("isExecutive")
    private boolean executive;
    private String status;
    private String firstGeneration;
    private List<GenerationSummary> generations;
    private List<MemberTagSummary> tags;

    @Getter
    @AllArgsConstructor
    public static class MemberTagSummary {
        private Long tagId;
        private String name;
        private String tagType;
        private String permissionLevel;
        private Long teamId;
        private String teamName;
    }

    @Getter
    @AllArgsConstructor
    public static class GenerationSummary {
        private Long id;
        private String name;
        private int displayOrder;
    }

    public static ClubMemberResponse from(ClubMembership cm, List<MemberTag> memberTags) {
        List<MemberTagSummary> tagSummaries = memberTags.stream()
                .map(mt -> new MemberTagSummary(
                        mt.getId(),
                        mt.getName(),
                        mt.getTagType().name(),
                        mt.getPermissionLevel().name(),
                        mt.getTeam() != null ? mt.getTeam().getId() : null,
                        mt.getTeam() != null ? mt.getTeam().getName() : null
                ))
                .toList();

        boolean executive = cm.isExecutive();

        // 기수 정보
        List<GenerationSummary> genList = cm.getGenerations().stream()
                .sorted(Comparator.comparingInt(SemiCategory::getDisplayOrder))
                .map(g -> new GenerationSummary(g.getId(), g.getName(), g.getDisplayOrder()))
                .toList();
        String firstGen = genList.isEmpty() ? null : genList.get(0).getName();

        var user = cm.getUser();
        return ClubMemberResponse.builder()
                .membershipId(cm.getId())
                .userId(user != null ? user.getId() : null)
                .name(cm.getName())
                .studentId(cm.getStudentId())
                .permissionLevel(executive ? "EXECUTIVE" : "REGULAR")
                .executive(executive)
                .status(cm.getStatus().name())
                .firstGeneration(firstGen)
                .generations(genList)
                .tags(tagSummaries)
                .build();
    }
}
