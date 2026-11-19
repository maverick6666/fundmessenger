package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.FundAuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FundAuditLogResponse {

    private Long id;
    private Long positionId;
    private Long actorId;
    private String actorName;
    private String action;
    private String detail;
    private LocalDateTime createdAt;

    public static FundAuditLogResponse from(FundAuditLog log) {
        return FundAuditLogResponse.builder()
                .id(log.getId())
                .positionId(log.getPosition() != null ? log.getPosition().getId() : null)
                .actorId(log.getActorId())
                .actorName(log.getActorName())
                .action(log.getAction())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
