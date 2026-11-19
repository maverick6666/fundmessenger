package com.fundmessenger.fund.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class BenchmarkComparisonResponse {

    private List<DataPoint> portfolio;
    private List<DataPoint> kospi;
    private List<DataPoint> sp500;

    @Getter
    @Builder
    public static class DataPoint {
        private String date;
        private BigDecimal cumulativeReturn; // 예: 0.05 = +5%
    }
}
