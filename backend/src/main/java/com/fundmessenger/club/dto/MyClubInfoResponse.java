package com.fundmessenger.club.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class MyClubInfoResponse {
    private Long clubId;
    private String clubName;
    private String clubLogoUrl;
    private String schoolName;
    private Long membershipId;
    private String myName;
    @JsonProperty("isExecutive")
    private boolean executive;
    private long totalMembers;
    private long totalTeams;
    private long myPostCount;
    private long myFileCount;
    private List<MyTagSummary> myTags;

    @Getter
    @AllArgsConstructor
    public static class MyTagSummary {
        private Long tagId;
        private String name;
        private String tagType;
        private String permissionLevel;
        private Long teamId;
        private String teamName;
        private String teamType;
        private boolean systemTeam;
    }
}
