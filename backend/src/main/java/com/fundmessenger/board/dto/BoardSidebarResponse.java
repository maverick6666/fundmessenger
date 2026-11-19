package com.fundmessenger.board.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BoardSidebarResponse {

    private List<BoardSidebarItem> boards;
    private List<TeamSidebarItem> teams;
    private List<SemiCategoryResponse> tags;

    @Getter
    @Builder
    public static class BoardSidebarItem {
        private Long id;
        private String name;
        private String boardType;
        private long postCount;
        private boolean canWrite;
    }

    @Getter
    @Builder
    public static class TeamSidebarItem {
        private Long id;
        private String name;
        private String description;
        private long memberCount;
        private long postCount;
        private String teamType;
        private boolean systemTeam;
    }
}
