package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.AssetSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AssetSnapshotResponse {

    private Long id;
    private Long teamId;
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
    private LocalDateTime createdAt;

    public static AssetSnapshotResponse from(AssetSnapshot snapshot) {
        return AssetSnapshotResponse.builder()
                .id(snapshot.getId())
                .teamId(snapshot.getTeam().getId())
                .snapshotDate(snapshot.getSnapshotDate())
                .krwCash(snapshot.getKrwCash())
                .krwEvaluation(snapshot.getKrwEvaluation())
                .usdCash(snapshot.getUsdCash())
                .usdEvaluation(snapshot.getUsdEvaluation())
                .totalKrw(snapshot.getTotalKrw())
                .exchangeRate(snapshot.getExchangeRate())
                .realizedPnl(snapshot.getRealizedPnl())
                .unrealizedPnl(snapshot.getUnrealizedPnl())
                .positionDetails(snapshot.getPositionDetails())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}
