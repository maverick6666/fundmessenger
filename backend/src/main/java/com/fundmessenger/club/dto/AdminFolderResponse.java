package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.AdminFolder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminFolderResponse {
    private Long id;
    private String name;
    private String folderType;
    private Integer displayOrder;
    private boolean collapsed;

    public static AdminFolderResponse from(AdminFolder f) {
        return new AdminFolderResponse(
            f.getId(),
            f.getName(),
            f.getFolderType().name(),
            f.getDisplayOrder(),
            f.isCollapsed()
        );
    }
}
