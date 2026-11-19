package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.PostComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostCommentResponse {
    private Long id;
    private Long postId;
    private Long parentCommentId;
    private Long authorMembershipId;
    private String authorName;
    private String content;
    private boolean aiGenerated;
    private LocalDateTime createdAt;

    public static PostCommentResponse from(PostComment comment) {
        return PostCommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
                .authorMembershipId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .content(comment.getContent())
                .aiGenerated(comment.isAiGenerated())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
