package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PostDetailResponse {
    private Long id;
    private List<PostResponse.BoardItem> boards;
    private String title;
    private String content;
    private String contentJson;
    private String authorName;
    private Long authorMembershipId;
    private List<SemiCategoryResponse> semiCategories;
    private List<PostResponse.TeamItem> teams;
    private Long linkedDriveFileId;
    private String linkedDriveFileName;
    private String linkedDriveFileStoredName;
    private String linkedDriveFileUrl;
    private boolean aiGenerated;
    private boolean aiEdited;
    private int likeCount;
    private boolean liked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostDetailResponse from(Post post) {
        return from(post, false);
    }

    public static PostDetailResponse from(Post post, boolean liked) {
        List<SemiCategoryResponse> categories = post.getSemiCategories().stream()
                .map(SemiCategoryResponse::from)
                .collect(Collectors.toList());

        List<PostResponse.BoardItem> boardItems = post.getBoards().stream()
                .map(b -> PostResponse.BoardItem.builder().id(b.getId()).name(b.getName()).build())
                .collect(Collectors.toList());

        List<PostResponse.TeamItem> teamItems = post.getTeams().stream()
                .map(t -> PostResponse.TeamItem.builder().teamId(t.getId()).teamName(t.getName()).build())
                .collect(Collectors.toList());

        return PostDetailResponse.builder()
                .id(post.getId())
                .boards(boardItems)
                .title(post.getTitle())
                .content(post.getContent())
                .contentJson(post.getContentJson())
                .authorName(post.getAuthor().getName())
                .authorMembershipId(post.getAuthor().getId())
                .semiCategories(categories)
                .teams(teamItems)
                .linkedDriveFileId(post.getLinkedDriveFile() != null ? post.getLinkedDriveFile().getId() : null)
                .linkedDriveFileName(post.getLinkedDriveFile() != null ? post.getLinkedDriveFile().getOriginalName() : null)
                .linkedDriveFileStoredName(post.getLinkedDriveFile() != null ? post.getLinkedDriveFile().getStoredName() : null)
                .aiGenerated(post.isAiGenerated())
                .aiEdited(post.isAiEdited())
                .likeCount(post.getLikeCount())
                .liked(liked)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
