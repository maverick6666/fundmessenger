package com.fundmessenger.fund.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberRankingResponse {

    private Long membershipId;
    private String memberName;
    private int totalTrades;
    private BigDecimal winRate;
    private BigDecimal realizedPnl;
    private BigDecimal avgProfitRate;
}
