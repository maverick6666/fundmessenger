package com.fundmessenger.fund.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyStatsResponse {

    private int totalTrades;
    private BigDecimal winRate;
    private BigDecimal avgProfitRate;

    // 최고/최저 거래
    private String bestTradeTicker;
    private BigDecimal bestTradeProfitRate;
    private String worstTradeTicker;
    private BigDecimal worstTradeProfitRate;

    // 평균 보유 시간
    private BigDecimal avgHoldingHours;

    private int successCount;
    private int failCount;
}
