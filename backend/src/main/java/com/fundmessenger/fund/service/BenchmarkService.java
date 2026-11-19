package com.fundmessenger.fund.service;

import com.fundmessenger.fund.dto.BenchmarkComparisonResponse;
import com.fundmessenger.fund.dto.BenchmarkComparisonResponse.DataPoint;
import com.fundmessenger.fund.entity.AssetSnapshot;
import com.fundmessenger.fund.repository.AssetSnapshotRepository;
import com.fundmessenger.jongtobang.dto.StockChartResponse;
import com.fundmessenger.jongtobang.service.StockChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final AssetSnapshotRepository snapshotRepository;
    private final StockChartService stockChartService;

    // 벤치마크 캐시 (30분 TTL) — 모든 팀 공유
    private final Map<String, CachedData> benchmarkCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30분

    public BenchmarkComparisonResponse compare(Long teamId, String range) {
        // 1. 날짜 범위 계산
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = switch (range) {
            case "6M" -> endDate.minusMonths(6);
            case "1Y" -> endDate.minusYears(1);
            case "ALL" -> endDate.minusYears(10);
            default -> endDate.minusMonths(3); // 3M
        };

        // 2. 포트폴리오 스냅샷 → 누적수익률
        List<AssetSnapshot> snapshots = snapshotRepository
                .findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(teamId, startDate, endDate);

        List<DataPoint> portfolio = calculatePortfolioReturns(snapshots);

        // 3. KOSPI 벤치마크
        String period = rangeToPeriod(range);
        List<DataPoint> kospi = fetchBenchmarkReturns("KOSPI_INDEX", () ->
                stockChartService.fetchDomesticIndexChart("0001", period));

        // 4. S&P 500 (SPY ETF 프록시)
        List<DataPoint> sp500 = fetchBenchmarkReturns("SPY_" + period, () ->
                stockChartService.fetchOverseasChartByCode("SPY", "AMEX", period));

        return BenchmarkComparisonResponse.builder()
                .portfolio(portfolio)
                .kospi(kospi)
                .sp500(sp500)
                .build();
    }

    private List<DataPoint> calculatePortfolioReturns(List<AssetSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();

        BigDecimal baseValue = snapshots.get(0).getTotalKrw();
        if (baseValue == null || baseValue.compareTo(BigDecimal.ZERO) == 0) return List.of();

        List<DataPoint> points = new ArrayList<>();
        for (AssetSnapshot snap : snapshots) {
            BigDecimal total = snap.getTotalKrw();
            if (total == null) continue;

            BigDecimal cumReturn = total.divide(baseValue, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);

            points.add(DataPoint.builder()
                    .date(snap.getSnapshotDate().toString())
                    .cumulativeReturn(cumReturn)
                    .build());
        }
        return points;
    }

    private List<DataPoint> fetchBenchmarkReturns(String cacheKey, java.util.function.Supplier<List<StockChartResponse.PricePoint>> fetcher) {
        // 만료 엔트리 정리 (lazy eviction)
        long now = System.currentTimeMillis();
        benchmarkCache.entrySet().removeIf(e -> now - e.getValue().timestamp >= CACHE_TTL_MS);

        // 캐시 확인
        CachedData cached = benchmarkCache.get(cacheKey);
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.data;
        }

        try {
            List<StockChartResponse.PricePoint> prices = fetcher.get();
            if (prices == null || prices.isEmpty()) return List.of();

            BigDecimal basePrice = prices.get(0).getClose();
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) == 0) return List.of();

            List<DataPoint> points = new ArrayList<>();
            for (StockChartResponse.PricePoint p : prices) {
                if (p.getClose() == null) continue;
                BigDecimal cumReturn = p.getClose().divide(basePrice, 6, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE);
                points.add(DataPoint.builder()
                        .date(p.getDate())
                        .cumulativeReturn(cumReturn)
                        .build());
            }

            benchmarkCache.put(cacheKey, new CachedData(points, System.currentTimeMillis()));
            return points;
        } catch (Exception e) {
            log.warn("벤치마크 데이터 조회 실패: key={}, error={}", cacheKey, e.getMessage());
            return List.of();
        }
    }

    private String rangeToPeriod(String range) {
        return switch (range) {
            case "1Y", "ALL" -> "monthly";
            case "6M" -> "weekly";
            default -> "daily";
        };
    }

    private record CachedData(List<DataPoint> data, long timestamp) {}
}
