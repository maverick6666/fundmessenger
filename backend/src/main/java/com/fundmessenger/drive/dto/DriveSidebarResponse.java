package com.fundmessenger.drive.dto;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DriveSidebarResponse {

    private List<TeamSidebarItem> teams;
    private List<FolderSidebarItem> folders;
    private List<SemiCategoryResponse> tags;

    @Getter
    @Builder
    public static class TeamSidebarItem {
        private Long id;     // null = 무소속
        private String name;
        private String description;
        private long memberCount;
        private long fileCount;
        private boolean systemTeam;
    }

    @Getter
    @Builder
    public static class FolderSidebarItem {
        private Long id;
        private String name;
        private String description;
        private long fileCount;
    }
}
