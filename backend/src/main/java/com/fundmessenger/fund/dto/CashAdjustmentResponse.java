package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.CashAdjustment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CashAdjustmentResponse {

    private Long id;
    private Long teamId;
    private String adjustmentType;
    private String currency;
    private BigDecimal amount;
    private String memo;
    private Long recordedById;
    private String recordedByName;
    private LocalDateTime createdAt;

    public static CashAdjustmentResponse from(CashAdjustment adj) {
        return CashAdjustmentResponse.builder()
                .id(adj.getId())
                .teamId(adj.getTeam().getId())
                .adjustmentType(adj.getAdjustmentType().name())
                .currency(adj.getCurrency())
                .amount(adj.getAmount())
                .memo(adj.getMemo())
                .recordedById(adj.getRecordedBy().getId())
                .recordedByName(adj.getRecordedBy().getName())
                .createdAt(adj.getCreatedAt())
                .build();
    }
}
