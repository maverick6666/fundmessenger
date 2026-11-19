package com.fundmessenger.jongtobang.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StockChartResponse {

    private String stockCode;
    private String period; // daily, weekly, monthly
    private List<PricePoint> prices;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class PricePoint {
        private String date;       // "2026-03-15"
        private BigDecimal close;  // 종가
        private BigDecimal open;   // 시가
        private BigDecimal high;   // 고가
        private BigDecimal low;    // 저가
        private Long volume;       // 거래량
    }
}
