package com.fundmessenger.drive.dto;

import com.fundmessenger.drive.entity.DriveComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DriveCommentResponse {
    private Long id;
    private Long fileId;
    private Long authorMembershipId;
    private String authorName;
    private String content;
    private Integer pageNumber;
    private LocalDateTime createdAt;

    public static DriveCommentResponse from(DriveComment comment) {
        return DriveCommentResponse.builder()
                .id(comment.getId())
                .fileId(comment.getFile().getId())
                .authorMembershipId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .content(comment.getContent())
                .pageNumber(comment.getPageNumber())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
