package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.CreditHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class CreditHistoryResponse {
    private Long id;
    private Integer amount;
    private String type;
    private String teamName;
    private String description;
    private LocalDateTime createdAt;

    public static CreditHistoryResponse from(CreditHistory h) {
        return CreditHistoryResponse.builder()
                .id(h.getId())
                .amount(h.getAmount())
                .type(h.getType().name())
                .teamName(h.getTeam() != null ? h.getTeam().getName() : null)
                .description(h.getDescription())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
