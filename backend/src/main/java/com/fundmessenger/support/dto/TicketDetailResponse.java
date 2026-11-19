package com.fundmessenger.support.dto;

import com.fundmessenger.support.entity.SupportTicket;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TicketDetailResponse {
    private Long id;
    private String title;
    private String content;
    private String category;
    private String status;
    private String userEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReplyResponse> replies;

    public static TicketDetailResponse from(SupportTicket ticket, List<ReplyResponse> replies) {
        return TicketDetailResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .content(ticket.getContent())
                .category(ticket.getCategory().name())
                .status(ticket.getStatus().name())
                .userEmail(ticket.getUser().getEmail())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .replies(replies)
                .build();
    }
}
