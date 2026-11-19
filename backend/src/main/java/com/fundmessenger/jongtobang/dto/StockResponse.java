package com.fundmessenger.jongtobang.dto;

import com.fundmessenger.jongtobang.entity.Stock;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class StockResponse {
    private Long id;
    private String stockCode;
    private String stockName;
    private String market;
    private BigDecimal currentPrice;
    private BigDecimal changeRate;
    private LocalDateTime priceUpdatedAt;
    private long newsCount;
    private long threadCount;
    private boolean hasNewNews;

    public static StockResponse from(Stock stock, long newsCount, long threadCount) {
        return StockResponse.builder()
                .id(stock.getId())
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .market(stock.getMarket())
                .currentPrice(stock.getCurrentPrice())
                .changeRate(stock.getChangeRate())
                .priceUpdatedAt(stock.getPriceUpdatedAt())
                .newsCount(newsCount)
                .threadCount(threadCount)
                .build();
    }
}
