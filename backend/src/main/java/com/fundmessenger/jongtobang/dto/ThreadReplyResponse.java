package com.fundmessenger.jongtobang.dto;

import com.fundmessenger.jongtobang.entity.ThreadReply;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ThreadReplyResponse {
    private Long id;
    private Long threadId;
    private Long parentReplyId;
    private Long authorMembershipId;
    private String authorName;
    private String content;
    private boolean aiGenerated;
    private LocalDateTime createdAt;

    public static ThreadReplyResponse from(ThreadReply reply) {
        return ThreadReplyResponse.builder()
                .id(reply.getId())
                .threadId(reply.getThread().getId())
                .parentReplyId(reply.getParentReply() != null ? reply.getParentReply().getId() : null)
                .authorMembershipId(reply.getAuthor().getId())
                .authorName(reply.getAuthor().getName())
                .content(reply.getContent())
                .aiGenerated(reply.isAiGenerated())
                .createdAt(reply.getCreatedAt())
                .build();
    }
}
