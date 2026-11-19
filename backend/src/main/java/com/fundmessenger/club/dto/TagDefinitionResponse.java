package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.TagDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class TagDefinitionResponse {
    private Long id;
    private String name;
    private String tagType;
    private String permissionLevel;
    private Long teamId;
    private String teamName;
    private int displayOrder;
    private long memberCount;
    private Long folderId;

    public static TagDefinitionResponse from(TagDefinition td, long memberCount) {
        return TagDefinitionResponse.builder()
                .id(td.getId())
                .name(td.getName())
                .tagType(td.getTagType().name())
                .permissionLevel(td.getPermissionLevel().name())
                .teamId(td.getTeam() != null ? td.getTeam().getId() : null)
                .teamName(td.getTeam() != null ? td.getTeam().getName() : null)
                .displayOrder(td.getDisplayOrder())
                .memberCount(memberCount)
                .folderId(td.getAdminFolder() != null ? td.getAdminFolder().getId() : null)
                .build();
    }
}
