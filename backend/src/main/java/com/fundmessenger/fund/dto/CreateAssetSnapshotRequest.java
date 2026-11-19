package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CreateAssetSnapshotRequest {

    @NotNull
    private LocalDate snapshotDate;

    private BigDecimal krwCash;
    private BigDecimal krwEvaluation;
    private BigDecimal usdCash;
    private BigDecimal usdEvaluation;
    private BigDecimal totalKrw;
    private BigDecimal exchangeRate;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private List<Map<String, Object>> positionDetails;
}
