package com.fundmessenger.drive.dto;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.drive.entity.DriveFile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DriveFileResponse {
    private Long id;
    private String originalName;
    private String storedName;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;
    private String description;
    private List<TeamItem> teams;
    private String uploaderName;
    private Long uploaderMembershipId;
    private List<SemiCategoryResponse> semiCategories;
    private List<FolderItem> folders;
    private Long linkedPostId;
    private String linkedPostTitle;
    private LocalDateTime createdAt;
    private long commentCount;

    @Getter
    @Builder
    public static class TeamItem {
        private Long teamId;
        private String teamName;
    }

    @Getter
    @Builder
    public static class FolderItem {
        private Long folderId;
        private String folderName;
    }

    public static DriveFileResponse from(DriveFile file) {
        return from(file, 0);
    }

    public static DriveFileResponse from(DriveFile file, long commentCount) {
        List<TeamItem> teamItems = file.getTeams().stream()
                .map(t -> TeamItem.builder().teamId(t.getId()).teamName(t.getName()).build())
                .collect(Collectors.toList());

        List<FolderItem> folderItems = file.getFolders().stream()
                .map(f -> FolderItem.builder().folderId(f.getId()).folderName(f.getName()).build())
                .collect(Collectors.toList());

        return DriveFileResponse.builder()
                .id(file.getId())
                .originalName(file.getOriginalName())
                .storedName(file.getStoredName())
                .fileUrl(null) // FileStorageService.getPublicUrl()로 세팅됨
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .description(file.getDescription())
                .teams(teamItems)
                .uploaderName(file.getUploader().getName())
                .uploaderMembershipId(file.getUploader().getId())
                .semiCategories(file.getSemiCategories().stream()
                        .map(SemiCategoryResponse::from)
                        .collect(Collectors.toList()))
                .folders(folderItems)
                .linkedPostId(file.getLinkedPost() != null ? file.getLinkedPost().getId() : null)
                .linkedPostTitle(file.getLinkedPost() != null ? file.getLinkedPost().getTitle() : null)
                .createdAt(file.getCreatedAt())
                .commentCount(commentCount)
                .build();
    }
}
