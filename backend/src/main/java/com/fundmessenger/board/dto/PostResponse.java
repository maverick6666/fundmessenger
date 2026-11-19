package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PostResponse {
    private Long id;
    private List<BoardItem> boards;
    private String title;
    private String authorName;
    private Long authorMembershipId;
    private List<SemiCategoryResponse> semiCategories;
    private List<TeamItem> teams;
    private boolean aiGenerated;
    private boolean aiEdited;
    private int likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class BoardItem {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class TeamItem {
        private Long teamId;
        private String teamName;
    }

    public static PostResponse from(Post post) {
        List<SemiCategoryResponse> categories = post.getSemiCategories().stream()
                .map(SemiCategoryResponse::from)
                .collect(Collectors.toList());

        List<BoardItem> boardItems = post.getBoards().stream()
                .map(b -> BoardItem.builder().id(b.getId()).name(b.getName()).build())
                .collect(Collectors.toList());

        List<TeamItem> teamItems = post.getTeams().stream()
                .map(t -> TeamItem.builder().teamId(t.getId()).teamName(t.getName()).build())
                .collect(Collectors.toList());

        return PostResponse.builder()
                .id(post.getId())
                .boards(boardItems)
                .title(post.getTitle())
                .authorName(post.getAuthor().getName())
                .authorMembershipId(post.getAuthor().getId())
                .semiCategories(categories)
                .teams(teamItems)
                .aiGenerated(post.isAiGenerated())
                .aiEdited(post.isAiEdited())
                .likeCount(post.getLikeCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
