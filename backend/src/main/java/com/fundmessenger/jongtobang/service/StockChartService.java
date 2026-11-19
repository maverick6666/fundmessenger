package com.fundmessenger.jongtobang.service;

import com.fundmessenger.jongtobang.dto.StockChartResponse;
import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockChartService {

    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** 차트 캐시: key = "stockId:period", value = 캐시 엔트리 */
    private final ConcurrentHashMap<String, ChartCacheEntry> chartCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30분

    private record ChartCacheEntry(StockChartResponse response, long cachedAt) {
        boolean isExpired() { return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS; }
    }

    @Value("${kis.app-key:}")
    private String appKey;

    @Value("${kis.app-secret:}")
    private String appSecret;

    @Value("${kis.base-url:https://openapi.koreainvestment.com:9443}")
    private String baseUrl;

    private static final DateTimeFormatter KIS_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 종목 차트 데이터 조회 (KIS 일봉/주봉/월봉)
     */
    public StockChartResponse getChart(Long stockId, String period) {
        String cacheKey = stockId + ":" + period;

        // 캐시 확인
        ChartCacheEntry cached = chartCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.response();
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다: " + stockId));

        if (isKisAvailable()) {
            try {
                List<StockChartResponse.PricePoint> prices;
                if (isKoreanStock(stock)) {
                    prices = fetchKisDomesticChart(stock, period);
                } else {
                    prices = fetchKisOverseasChart(stock, period);
                }
                if (prices != null && !prices.isEmpty()) {
                    StockChartResponse response = StockChartResponse.builder()
                            .stockCode(stock.getStockCode())
                            .period(period)
                            .prices(prices)
                            .build();
                    chartCache.put(cacheKey, new ChartCacheEntry(response, System.currentTimeMillis()));
                    return response;
                }
            } catch (Exception e) {
                log.warn("KIS 차트 조회 실패, 빈 데이터 반환: {} ({})", stock.getStockCode(), e.getMessage());
            }
        }

        return StockChartResponse.builder()
                .stockCode(stock.getStockCode())
                .period(period)
                .prices(List.of())
                .build();
    }

    /**
     * KIS 국내주식 기간별시세 API 호출
     * tr_id: FHKST03010100 (일/주/월봉)
     */
    private List<StockChartResponse.PricePoint> fetchKisDomesticChart(Stock stock, String period) {
        String accessToken = stockPriceService.getAccessToken();
        if (accessToken == null) return null;

        String fid = "J"; // KIS 기간별시세 API는 KOSPI/KOSDAQ 모두 "J" 사용

        // 기간 설정
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        String periodCode;

        switch (period) {
            case "weekly" -> {
                startDate = endDate.minusWeeks(26); // 6개월
                periodCode = "W";
            }
            case "monthly" -> {
                startDate = endDate.minusMonths(24); // 2년
                periodCode = "M";
            }
            default -> { // daily
                startDate = endDate.minusDays(90); // 3개월
                periodCode = "D";
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST03010100"); // 기간별시세

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?FID_COND_MRKT_DIV_CODE=" + fid
                + "&FID_INPUT_ISCD=" + stock.getStockCode()
                + "&FID_INPUT_DATE_1=" + startDate.format(KIS_DATE_FMT)
                + "&FID_INPUT_DATE_2=" + endDate.format(KIS_DATE_FMT)
                + "&FID_PERIOD_DIV_CODE=" + periodCode
                + "&FID_ORG_ADJ_PRC=0"; // 수정주가 반영

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.getBody().get("output2");
        if (output2 == null || output2.isEmpty()) return null;

        List<StockChartResponse.PricePoint> points = new ArrayList<>();
        DateTimeFormatter isoFmt = DateTimeFormatter.ISO_LOCAL_DATE;

        for (Map<String, Object> item : output2) {
            String dateStr = String.valueOf(item.get("stck_bsop_date")); // yyyyMMdd
            if (dateStr.length() != 8) continue;

            String isoDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);

            points.add(StockChartResponse.PricePoint.builder()
                    .date(isoDate)
                    .open(new BigDecimal(String.valueOf(item.get("stck_oprc"))))   // 시가
                    .high(new BigDecimal(String.valueOf(item.get("stck_hgpr"))))   // 고가
                    .low(new BigDecimal(String.valueOf(item.get("stck_lwpr"))))    // 저가
                    .close(new BigDecimal(String.valueOf(item.get("stck_clpr"))))  // 종가
                    .volume(Long.parseLong(String.valueOf(item.get("acml_vol")))) // 거래량
                    .build());
        }

        // KIS는 최신→과거 순서로 반환 → 오래된 순으로 뒤집기
        java.util.Collections.reverse(points);

        return points;
    }

    /**
     * KIS 해외주식 기간별시세 API 호출
     * tr_id: HHDFS76240000 (일/주/월봉)
     */
    private List<StockChartResponse.PricePoint> fetchKisOverseasChart(Stock stock, String period) {
        String accessToken = stockPriceService.getAccessToken();
        if (accessToken == null) return null;

        String exchCode = switch (stock.getMarket()) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> null;
        };
        if (exchCode == null) return null;

        // 기간 구분: 0=일, 1=주, 2=월
        String gubn = switch (period) {
            case "weekly" -> "1";
            case "monthly" -> "2";
            default -> "0"; // daily
        };

        // 조회 기준일 (빈 문자열이면 오늘 기준)
        String bymd = "";

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76240000");

        String url = baseUrl + "/uapi/overseas-price/v1/quotations/dailyprice"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&SYMB=" + stock.getStockCode()
                + "&GUBN=" + gubn
                + "&BYMD=" + bymd
                + "&MODP=1"; // 수정주가 반영

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.getBody().get("output2");
        if (output2 == null || output2.isEmpty()) return null;

        List<StockChartResponse.PricePoint> points = new ArrayList<>();

        for (Map<String, Object> item : output2) {
            String dateStr = String.valueOf(item.get("xymd")); // yyyyMMdd
            if (dateStr.length() != 8) continue;

            String isoDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);

            String openStr = String.valueOf(item.get("open"));
            String highStr = String.valueOf(item.get("high"));
            String lowStr = String.valueOf(item.get("low"));
            String closeStr = String.valueOf(item.get("clos"));
            String volStr = String.valueOf(item.get("tvol"));

            if (closeStr == null || "null".equals(closeStr) || closeStr.isBlank()) continue;

            try {
                points.add(StockChartResponse.PricePoint.builder()
                        .date(isoDate)
                        .open(new BigDecimal(openStr))
                        .high(new BigDecimal(highStr))
                        .low(new BigDecimal(lowStr))
                        .close(new BigDecimal(closeStr))
                        .volume(Long.parseLong(volStr))
                        .build());
            } catch (NumberFormatException e) {
                log.debug("해외 차트 데이터 파싱 실패: date={}", dateStr);
            }
        }

        // KIS는 최신→과거 순서로 반환 → 오래된 순으로 뒤집기
        java.util.Collections.reverse(points);

        return points;
    }

    /**
     * 국내 업종/지수 차트 데이터 조회 (예: KOSPI=0001)
     * FID_COND_MRKT_DIV_CODE=U (업종)
     */
    public List<StockChartResponse.PricePoint> fetchDomesticIndexChart(String indexCode, String period) {
        if (!isKisAvailable()) return List.of();

        String accessToken = stockPriceService.getAccessToken();
        if (accessToken == null) return List.of();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        String periodCode;

        switch (period) {
            case "weekly" -> { startDate = endDate.minusWeeks(26); periodCode = "W"; }
            case "monthly" -> { startDate = endDate.minusMonths(24); periodCode = "M"; }
            default -> { startDate = endDate.minusDays(90); periodCode = "D"; }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST03010100");

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?FID_COND_MRKT_DIV_CODE=U"
                + "&FID_INPUT_ISCD=" + indexCode
                + "&FID_INPUT_DATE_1=" + startDate.format(KIS_DATE_FMT)
                + "&FID_INPUT_DATE_2=" + endDate.format(KIS_DATE_FMT)
                + "&FID_PERIOD_DIV_CODE=" + periodCode
                + "&FID_ORG_ADJ_PRC=0";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return List.of();

            List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.getBody().get("output2");
            if (output2 == null || output2.isEmpty()) return List.of();

            List<StockChartResponse.PricePoint> points = new ArrayList<>();
            for (Map<String, Object> item : output2) {
                String dateStr = String.valueOf(item.get("stck_bsop_date"));
                if (dateStr.length() != 8) continue;
                String isoDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
                try {
                    points.add(StockChartResponse.PricePoint.builder()
                            .date(isoDate)
                            .open(new BigDecimal(String.valueOf(item.get("stck_oprc"))))
                            .high(new BigDecimal(String.valueOf(item.get("stck_hgpr"))))
                            .low(new BigDecimal(String.valueOf(item.get("stck_lwpr"))))
                            .close(new BigDecimal(String.valueOf(item.get("stck_clpr"))))
                            .volume(Long.parseLong(String.valueOf(item.get("acml_vol"))))
                            .build());
                } catch (NumberFormatException e) {
                    log.debug("인덱스 차트 데이터 파싱 실패: date={}", dateStr);
                }
            }
            java.util.Collections.reverse(points);
            return points;
        } catch (Exception e) {
            log.warn("KIS 인덱스 차트 조회 실패: indexCode={}, error={}", indexCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 해외 종목 차트 (stockCode + market으로 직접 조회 — Stock 엔티티 없이)
     */
    public List<StockChartResponse.PricePoint> fetchOverseasChartByCode(String stockCode, String market, String period) {
        if (!isKisAvailable()) return List.of();

        String accessToken = stockPriceService.getAccessToken();
        if (accessToken == null) return List.of();

        String exchCode = switch (market) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> null;
        };
        if (exchCode == null) return List.of();

        String gubn = switch (period) {
            case "weekly" -> "1";
            case "monthly" -> "2";
            default -> "0";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76240000");

        String url = baseUrl + "/uapi/overseas-price/v1/quotations/dailyprice"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&SYMB=" + stockCode
                + "&GUBN=" + gubn
                + "&BYMD="
                + "&MODP=1";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return List.of();

            List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.getBody().get("output2");
            if (output2 == null || output2.isEmpty()) return List.of();

            List<StockChartResponse.PricePoint> points = new ArrayList<>();
            for (Map<String, Object> item : output2) {
                String dateStr = String.valueOf(item.get("xymd"));
                if (dateStr.length() != 8) continue;
                String isoDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
                String closeStr = String.valueOf(item.get("clos"));
                if (closeStr == null || "null".equals(closeStr) || closeStr.isBlank()) continue;
                try {
                    points.add(StockChartResponse.PricePoint.builder()
                            .date(isoDate)
                            .open(new BigDecimal(String.valueOf(item.get("open"))))
                            .high(new BigDecimal(String.valueOf(item.get("high"))))
                            .low(new BigDecimal(String.valueOf(item.get("low"))))
                            .close(new BigDecimal(closeStr))
                            .volume(Long.parseLong(String.valueOf(item.get("tvol"))))
                            .build());
                } catch (NumberFormatException e) {
                    log.debug("해외 차트 데이터 파싱 실패: date={}", dateStr);
                }
            }
            java.util.Collections.reverse(points);
            return points;
        } catch (Exception e) {
            log.warn("KIS 해외 차트 조회 실패: stockCode={}, error={}", stockCode, e.getMessage());
            return List.of();
        }
    }

    private boolean isKisAvailable() {
        return appKey != null && !appKey.isBlank();
    }

    private boolean isKoreanStock(Stock stock) {
        return "KOSPI".equals(stock.getMarket()) || "KOSDAQ".equals(stock.getMarket());
    }
}
