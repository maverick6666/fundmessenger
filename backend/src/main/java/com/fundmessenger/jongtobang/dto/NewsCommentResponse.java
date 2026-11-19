package com.fundmessenger.jongtobang.dto;

import com.fundmessenger.jongtobang.entity.NewsComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsCommentResponse {
    private Long id;
    private Long couplingId;
    private Long parentCommentId;
    private Long authorMembershipId;
    private String authorName;
    private String content;
    private boolean aiGenerated;
    private LocalDateTime createdAt;

    public static NewsCommentResponse from(NewsComment comment) {
        return NewsCommentResponse.builder()
                .id(comment.getId())
                .couplingId(comment.getCoupling().getId())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
                .authorMembershipId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .content(comment.getContent())
                .aiGenerated(comment.isAiGenerated())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
