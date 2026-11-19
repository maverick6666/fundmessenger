package com.fundmessenger.fund.controller;

import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.fund.dto.PriceQuoteResponse;
import com.fundmessenger.jongtobang.dto.StockChartResponse;
import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.repository.StockRepository;
import com.fundmessenger.jongtobang.service.StockChartService;
import com.fundmessenger.jongtobang.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/prices")
@RequiredArgsConstructor
public class FundPriceController {

    private final ClubAuthorizationService authService;
    private final StockRepository stockRepository;
    private final StockChartService stockChartService;
    private final StockPriceService stockPriceService;

    /**
     * 실시간 시세 조회 (ticker + market)
     * GET /api/clubs/{clubId}/fund/prices/quote?ticker=005930&market=KOSPI
     */
    @GetMapping("/quote")
    public ResponseEntity<PriceQuoteResponse> getQuote(
            @PathVariable Long clubId,
            @RequestParam String ticker,
            @RequestParam String market,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubMember(userId, clubId);

        Optional<Stock> stockOpt = stockRepository.findByStockCode(ticker);
        if (stockOpt.isEmpty()) {
            return ResponseEntity.ok(PriceQuoteResponse.builder()
                    .ticker(ticker)
                    .market(market)
                    .build());
        }

        Stock stock = stockOpt.get();

        // on-demand 가격 갱신 (5분 캐시)
        stockPriceService.refreshPriceOnDemand(stock);

        // previousClose 역산: currentPrice / (1 + changeRate/100)
        BigDecimal previousClose = null;
        if (stock.getCurrentPrice() != null && stock.getChangeRate() != null
                && stock.getChangeRate().compareTo(BigDecimal.valueOf(-100)) != 0) {
            previousClose = stock.getCurrentPrice().divide(
                    BigDecimal.ONE.add(stock.getChangeRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)),
                    2, RoundingMode.HALF_UP);
        }

        return ResponseEntity.ok(PriceQuoteResponse.builder()
                .ticker(stock.getStockCode())
                .market(stock.getMarket())
                .currentPrice(stock.getCurrentPrice())
                .previousClose(previousClose)
                .changeRate(stock.getChangeRate())
                .high(null)   // Stock 엔티티에 당일 고/저가 필드 없음
                .low(null)
                .volume(stock.getTradingVolume())
                .build());
    }

    /**
     * 캔들 차트 데이터 조회 (ticker + market)
     * GET /api/clubs/{clubId}/fund/prices/candles?ticker=005930&market=KOSPI&period=daily
     */
    @GetMapping("/candles")
    public ResponseEntity<StockChartResponse> getCandles(
            @PathVariable Long clubId,
            @RequestParam String ticker,
            @RequestParam String market,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) Long before,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubMember(userId, clubId);

        Optional<Stock> stockOpt = stockRepository.findByStockCode(ticker);
        if (stockOpt.isEmpty()) {
            return ResponseEntity.ok(StockChartResponse.builder()
                    .stockCode(ticker)
                    .period(period)
                    .prices(java.util.List.of())
                    .build());
        }

        Stock stock = stockOpt.get();
        StockChartResponse chart = stockChartService.getChart(stock.getId(), period);
        return ResponseEntity.ok(chart);
    }
}
