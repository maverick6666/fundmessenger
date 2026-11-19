package com.fundmessenger.support.dto;

import com.fundmessenger.support.entity.TicketReply;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReplyResponse {
    private Long id;
    private String content;
    private String authorEmail;
    private boolean adminReply;
    private LocalDateTime createdAt;

    public static ReplyResponse from(TicketReply reply) {
        return ReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .authorEmail(reply.getAuthor().getEmail())
                .adminReply(reply.isAdminReply())
                .createdAt(reply.getCreatedAt())
                .build();
    }
}
