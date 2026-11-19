package com.fundmessenger.jongtobang.service;

import com.fundmessenger.jongtobang.dto.StockResponse;
import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.repository.StockNewsCouplingRepository;
import com.fundmessenger.jongtobang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * KIS API 기반 주식 시세 조회 서비스
 * - 정기 갱신: 주목 종목(attention > 0)의 시세를 10분마다 갱신, 랭킹풀(시가총액/등락률/거래량) 갱신
 * - on-demand: 종목 상세 조회 시 개별 시세 갱신
 * - 랭킹 API: 등락률 랭킹 외에도 KIS 시가총액, 거래량 랭킹 조회를 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StockRepository stockRepository;
    private final StockNewsCouplingRepository couplingRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kis.app-key:}")
    private String appKey;

    @Value("${kis.app-secret:}")
    private String appSecret;

    @Value("${kis.base-url:https://openapi.koreainvestment.com:9443}")
    private String baseUrl;

    private String accessToken;
    private LocalDateTime tokenExpiry;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private final ReentrantLock rankingRefreshLock = new ReentrantLock();
    private final ReentrantLock backupLock = new ReentrantLock();

    private static final int RANKING_REFRESH_SIZE = 30;

    public String getAccessToken() {
        ensureAccessToken();
        return accessToken;
    }

    // ── 정기 갱신: 주목 종목(attention > 0) 시세 갱신 ──

    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    @Transactional
    public void refreshAttentionPrices() {
        if (!refreshLock.tryLock()) {
            log.warn("Another price refresh is already running, skipping");
            return;
        }
        try {
            if (appKey == null || appKey.isBlank()) return;

            List<Long> attentionIds = couplingRepository.findDistinctStockIds();
            if (attentionIds.isEmpty()) return;
            List<Stock> stocks = stockRepository.findAllById(attentionIds);
            if (stocks.isEmpty()) return;

            ensureAccessToken();
            if (accessToken == null) return;

            int updated = 0;
            for (Stock stock : stocks) {
                if (!stock.isActive() || stock.isEtf()) continue;
                try {
                    refreshPrice(stock);
                    updated++;
                    Thread.sleep(60);
                } catch (Exception e) {
                    log.warn("시세 갱신 실패: {} ({})", stock.getStockName(), stock.getStockCode(), e);
                }
            }
            if (updated > 0) {
                log.info("Attention price refresh complete: {} items", updated);
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean shouldRefreshOverseasQuote(Stock stock) {
        return stock.getCurrentPrice() == null
                || stock.getChangeRate() == null
                || stock.getPriceUpdatedAt() == null
                || stock.getPriceUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10));
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 45_000)
    @Transactional
    public void refreshRankingPools() {
        if (!rankingRefreshLock.tryLock()) {
            log.warn("Another ranking pool refresh is already running, skipping");
            return;
        }
        try {
            if (appKey == null || appKey.isBlank()) return;

            ensureAccessToken();
            if (accessToken == null) return;

            List<Stock> candidates = collectCandidateStocks();
            if (!candidates.isEmpty()) {
                log.info("Ranking pool refresh complete: {} items", candidates.size());
            }
        } catch (Exception e) {
            log.warn("Ranking pool refresh failed", e);
        } finally {
            rankingRefreshLock.unlock();
        }
    }

    // ── On-demand 시세 조회: 개별 종목 시세 갱신 ──

    @Transactional
    public void refreshPriceOnDemand(Stock stock) {
        if (appKey == null || appKey.isBlank()) return;
        if (stock.isEtf()) return;

        // 최근 5분 이내 갱신된 경우 스킵
        if (stock.getPriceUpdatedAt() != null
                && stock.getPriceUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return;
        }

        ensureAccessToken();
        if (accessToken == null) return;

        try {
            refreshPrice(stock);
        } catch (Exception e) {
            log.warn("on-demand 시세 갱신 실패: {} ({})", stock.getStockName(), stock.getStockCode(), e);
        }
    }

    // ── KIS 국내 등락률 랭킹 API (국내) ──

    @Transactional
    public List<StockResponse> getDomesticRanking(String marketCode, boolean ascending, int size) {
        if (appKey == null || appKey.isBlank()) return fallbackRankingResponses(marketCode, ascending, size);
        if (!isDomesticTradingHoursNow()) {
            return fallbackRankingResponses(marketCode, ascending, size);
        }

        ensureAccessToken();
        if (accessToken == null) return fallbackRankingResponses(marketCode, ascending, size);

        // J=KOSPI(유가증권), Q=KOSDAQ(코스닥). "J"로 KOSPI+KOSDAQ 모두 포함
        String fid = "J";
        String inputCode = switch (marketCode) {
            case "KOSPI" -> "0001";
            case "KOSDAQ" -> "1001";
            default -> "0000";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01700000");
        headers.set("custtype", "P");

        String sortCode = ascending ? "1" : "0";
        // fid_trgt_exls_cls_code: 10번째 7자리=ETF 제외, 8자리=ETN 제외
        // "0000000110" → ETF(7)+ETN(8) 제외
        // KOSPI: ETF+ETN 제외, KOSDAQ: 이 필터가 일반 종목까지 제외하는 KIS API 이슈로 필터 해제
        String exclCode = "KOSDAQ".equals(marketCode) ? "0" : "0000000110";

        String url = baseUrl + "/uapi/domestic-stock/v1/ranking/fluctuation"
                + "?FID_COND_MRKT_DIV_CODE=" + fid
                + "&FID_COND_SCR_DIV_CODE=20170"
                + "&FID_INPUT_ISCD=" + inputCode
                + "&FID_RANK_SORT_CLS_CODE=" + sortCode
                + "&FID_INPUT_CNT_1=0"
                + "&FID_PRC_CLS_CODE=0"
                + "&FID_INPUT_PRICE_1="
                + "&FID_INPUT_PRICE_2="
                + "&FID_VOL_CNT="
                + "&FID_TRGT_CLS_CODE=0"
                + "&FID_TRGT_EXLS_CLS_CODE=" + exclCode
                + "&FID_DIV_CLS_CODE=0"
                + "&FID_RSFL_RATE1="
                + "&FID_RSFL_RATE2=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackRankingResponses(marketCode, ascending, size);
                outputs.forEach(output -> ensureDomesticRankingStock(output, marketCode));

                List<StockResponse> ranked = outputs.stream()
                        .map(o -> mapDomesticRanking(o, marketCode))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                List<StockResponse> normalized = normalizeRankingResponses(ranked, ascending, size);

                if (normalized.isEmpty() || shouldFallbackDomesticRanking(marketCode, normalized)) {
                    log.warn("KIS 국내 등락률 랭킹 조회 결과 부족, fallback 사용: market={}, size={}, ranked={}", marketCode, size, normalized.size());
                    return fallbackRankingResponses(marketCode, ascending, size);
                }

                return topUpRankingResponses(normalized, marketCode, ascending, size);
            }
        } catch (Exception e) {
            log.warn("KIS 국내 등락률 랭킹 조회 실패: {}", e.getMessage());
        }
        return fallbackRankingResponses(marketCode, ascending, size);
    }

    private StockResponse mapDomesticRanking(Map<String, Object> output, String requestedMarket) {
        try {
            String code = String.valueOf(output.get("stck_shrn_iscd"));
            String name = String.valueOf(output.get("hts_kor_isnm"));
            String priceStr = String.valueOf(output.get("stck_prpr"));
            String rateStr = String.valueOf(output.get("prdy_ctrt"));

            // DB에서 종목 조회 후 최신 시세 업데이트
            Optional<Stock> stockOpt = stockRepository.findByStockCode(code);
            Stock stock;
            if (stockOpt.isPresent()) {
                stock = stockOpt.get();
                if (hasNumber(priceStr)) {
                    stock.setCurrentPrice(new BigDecimal(priceStr));
                }
                if (hasNumber(rateStr)) {
                    stock.setChangeRate(new BigDecimal(rateStr));
                }
                stock.setPriceUpdatedAt(LocalDateTime.now());
                stockRepository.save(stock);
            } else {
                // 새 종목이 DB에 없으면 종목 등록은 ensureDomesticRankingStock에서 처리하므로 랭킹에서 제외
                return null;
            }

            return toRankingResponse(stock);
        } catch (Exception e) {
            log.debug("국내 등락률 종목 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── KIS 해외 등락률 랭킹 API ──

    @Transactional
    public List<StockResponse> getOverseasRanking(String market, boolean ascending, int size) {
        if (appKey == null || appKey.isBlank()) return fallbackRankingResponses(market, ascending, size);

        ensureAccessToken();
        if (accessToken == null) return fallbackRankingResponses(market, ascending, size);

        String exchCode = switch (market) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> "NAS";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76260000"); // 해외주식 등락률 순위 메뉴 - 등락률 랭킹
        headers.set("custtype", "P");

        String gubn = ascending ? "0" : "1";

        String url = baseUrl + "/uapi/overseas-stock/v1/ranking/price-fluct"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&GUBN=" + gubn
                + "&MINX=8"      // 60일 거래량 필터 없음
                + "&VOL_RANG=0"  // 거래량 범위 전체 포함
                + "&KEYB=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output2");
                if (outputs == null) {
                    outputs = (List<Map<String, Object>>) response.getBody().get("output");
                }
                if (outputs == null) return fallbackRankingResponses(market, ascending, size);
                outputs.forEach(output -> ensureOverseasRankingStock(output, market));

                List<StockResponse> ranked = outputs.stream()
                        .map(o -> mapOverseasRanking(o, market))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                List<StockResponse> normalized = normalizeRankingResponses(ranked, ascending, size);
                if (normalized.isEmpty()) {
                    log.warn("KIS 해외 등락률 랭킹 조회 결과 부족, fallback 사용: market={}, size={}", market, size);
                    return fallbackRankingResponses(market, ascending, size);
                }
                return topUpRankingResponses(normalized, market, ascending, size);
            }
        } catch (Exception e) {
            log.warn("KIS 해외 등락률 랭킹 조회 실패: {}", e.getMessage());
        }
        return fallbackRankingResponses(market, ascending, size);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getCombinedDomesticRanking(boolean ascending, int size) {
        return mergeRankings(List.of(
                getDomesticRanking("KOSPI", ascending, size),
                getDomesticRanking("KOSDAQ", ascending, size)
        ), ascending, size);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getCombinedOverseasRanking(boolean ascending, int size) {
        return mergeRankings(List.of(
                getOverseasRanking("NASDAQ", ascending, size),
                getOverseasRanking("NYSE", ascending, size),
                getOverseasRanking("AMEX", ascending, size)
        ), ascending, size);
    }

    private StockResponse mapOverseasRanking(Map<String, Object> output, String market) {
        try {
            String code = String.valueOf(output.get("symb"));
            String name = String.valueOf(output.get("name"));
            if (name == null || "null".equals(name)) {
                name = String.valueOf(output.get("knam"));
            }
            String lastStr = String.valueOf(output.get("last"));
            String rateStr = String.valueOf(output.get("rate"));

            if (lastStr == null || "null".equals(lastStr) || lastStr.isBlank()) return null;

            Optional<Stock> stockOpt = stockRepository.findByStockCode(code);
            Stock stock;
            if (stockOpt.isPresent()) {
                stock = stockOpt.get();
                stock.setCurrentPrice(new BigDecimal(lastStr));
                stock.setChangeRate(new BigDecimal(rateStr));
                stock.setPriceUpdatedAt(LocalDateTime.now());
                stockRepository.save(stock);
            } else {
                return null;
            }

            return toRankingResponse(stock);
        } catch (Exception e) {
            log.debug("해외 등락률 종목 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── KIS 국내 시가총액 API (국내) ──

    @Transactional
    public List<Stock> getDomesticMarketCapRanking(int size) {
        if (appKey == null || appKey.isBlank()) return fallbackMarketCapStocks(null, size);
        ensureAccessToken();
        if (accessToken == null) return fallbackMarketCapStocks(null, size);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01740000");
        headers.set("custtype", "P");

        List<Stock> result = new ArrayList<>();
        for (String iscd : List.of("0001", "1001")) {
            String url = baseUrl + "/uapi/domestic-stock/v1/ranking/market-cap"
                    + "?fid_cond_mrkt_div_code=J"
                    + "&fid_cond_scr_div_code=20174"
                    + "&fid_input_iscd=" + iscd
                    + "&fid_div_cls_code=1"      // 시가총액 기준 정렬
                    + "&fid_trgt_cls_code=0"
                    + "&fid_trgt_exls_cls_code=0000000110"  // ETF+ETN 제외
                    + "&fid_input_price_1="
                    + "&fid_input_price_2="
                    + "&fid_vol_cnt=";

            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output");
                    if (outputs != null) {
                        String market = "0001".equals(iscd) ? "KOSPI" : "KOSDAQ";
                        for (Map<String, Object> o : outputs) {
                            if (result.size() >= size) break;
                            Stock s = updateStockFromMarketCapRanking(o, market);
                            if (s != null) result.add(s);
                        }
                    }
                }
                Thread.sleep(60);
            } catch (Exception e) {
                log.warn("KIS 국내 시가총액 랭킹 조회 실패 ({}): {}", iscd, e.getMessage());
            }
        }
        return topUpStocksByMarketCap(result, null, size);
    }

    private Stock updateStockFromMarketCapRanking(Map<String, Object> output, String market) {
        try {
            String code = String.valueOf(output.get("mksc_shrn_iscd")); // 종목코드
            if (code == null || "null".equals(code)) code = String.valueOf(output.get("stck_shrn_iscd"));
            String priceStr = String.valueOf(output.get("stck_prpr"));
            String rateStr = String.valueOf(output.get("prdy_ctrt"));
            String mcapStr = String.valueOf(output.get("stck_avls")); // 시가총액(억)
            String volStr = String.valueOf(output.get("acml_vol"));
            String trPbmnStr = String.valueOf(output.get("acml_tr_pbmn"));
            Optional<Stock> stockOpt = stockRepository.findByStockCode(code);
            if (stockOpt.isEmpty()) return null;

            Stock stock = stockOpt.get();
            if (priceStr != null && !"null".equals(priceStr) && !priceStr.isBlank())
                stock.setCurrentPrice(new BigDecimal(priceStr));
            if (rateStr != null && !"null".equals(rateStr) && !rateStr.isBlank())
                stock.setChangeRate(new BigDecimal(rateStr));
            if (mcapStr != null && !"null".equals(mcapStr) && !mcapStr.isBlank())
                stock.setMarketCap(new BigDecimal(mcapStr));
            if (volStr != null && !"null".equals(volStr) && !volStr.isBlank())
                stock.setTradingVolume(Long.parseLong(volStr));
            if (trPbmnStr != null && !"null".equals(trPbmnStr) && !trPbmnStr.isBlank())
                stock.setTradingValue(new BigDecimal(trPbmnStr));
            stock.setPriceUpdatedAt(LocalDateTime.now());
            stockRepository.save(stock);
            return stock;
        } catch (Exception e) {
            log.debug("시가총액 랭킹 종목 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── KIS 국내 거래량 랭킹 API (국내) ──

    @Transactional
    public List<Stock> getDomesticVolumeRanking(int size) {
        if (appKey == null || appKey.isBlank()) return fallbackVolumeStocks(null, size);
        ensureAccessToken();
        if (accessToken == null) return fallbackVolumeStocks(null, size);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01710000");
        headers.set("custtype", "P");

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/volume-rank"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_COND_SCR_DIV_CODE=20171"
                + "&FID_INPUT_ISCD=0000"
                + "&FID_DIV_CLS_CODE=0"
                + "&FID_BLNG_CLS_CODE=0"
                + "&FID_TRGT_CLS_CODE=111111111"
                + "&FID_TRGT_EXLS_CLS_CODE=000000"
                + "&FID_INPUT_PRICE_1="
                + "&FID_INPUT_PRICE_2="
                + "&FID_VOL_CNT="
                + "&FID_INPUT_DATE_1=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackVolumeStocks(null, size);

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> o : outputs) {
                    if (result.size() >= size) break;
                    Stock s = updateStockFromVolumeRanking(o);
                    if (s != null) result.add(s);
                }
                return topUpStocksByVolume(result, null, size);
            }
        } catch (Exception e) {
            log.warn("KIS 국내 거래량 랭킹 조회 실패: {}", e.getMessage());
        }
        return fallbackVolumeStocks(null, size);
    }

    private Stock updateStockFromVolumeRanking(Map<String, Object> output) {
        try {
            String code = String.valueOf(output.get("mksc_shrn_iscd"));
            if (code == null || "null".equals(code)) code = String.valueOf(output.get("stck_shrn_iscd"));
            String priceStr = String.valueOf(output.get("stck_prpr"));
            String rateStr = String.valueOf(output.get("prdy_ctrt"));
            String volStr = String.valueOf(output.get("acml_vol"));
            String trPbmnStr = String.valueOf(output.get("acml_tr_pbmn"));

            Optional<Stock> stockOpt = stockRepository.findByStockCode(code);
            if (stockOpt.isEmpty()) return null;

            Stock stock = stockOpt.get();
            if (priceStr != null && !"null".equals(priceStr) && !priceStr.isBlank())
                stock.setCurrentPrice(new BigDecimal(priceStr));
            if (rateStr != null && !"null".equals(rateStr) && !rateStr.isBlank())
                stock.setChangeRate(new BigDecimal(rateStr));
            if (volStr != null && !"null".equals(volStr) && !volStr.isBlank())
                stock.setTradingVolume(Long.parseLong(volStr));
            if (trPbmnStr != null && !"null".equals(trPbmnStr) && !trPbmnStr.isBlank())
                stock.setTradingValue(new BigDecimal(trPbmnStr));
            stock.setPriceUpdatedAt(LocalDateTime.now());
            stockRepository.save(stock);
            return stock;
        } catch (Exception e) {
            log.debug("거래량 랭킹 종목 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── KIS 해외 시가총액 API ──

    @Transactional
    public List<Stock> getOverseasMarketCapRanking(String market, int size) {
        if (appKey == null || appKey.isBlank()) return fallbackMarketCapStocks(market, size);
        ensureAccessToken();
        if (accessToken == null) return fallbackMarketCapStocks(market, size);

        String exchCode = switch (market) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> "NAS";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76350100");
        headers.set("custtype", "P");

        String url = baseUrl + "/uapi/overseas-stock/v1/ranking/market-cap"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&VOL_RANG=0"
                + "&KEYB=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output2");
                if (outputs == null) outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackMarketCapStocks(market, size);

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> o : outputs) {
                    if (result.size() >= size) break;
                    ensureOverseasRankingStock(o, market);
                    Stock s = updateStockFromOverseasRanking(o, market);
                    if (s != null) result.add(s);
                }
                return topUpStocksByMarketCap(result, market, size);
            }
        } catch (Exception e) {
            log.warn("KIS 해외 시가총액 랭킹 조회 실패 ({}): {}", market, e.getMessage());
        }
        return fallbackMarketCapStocks(market, size);
    }

    // ── KIS 해외 거래량 랭킹 API ──

    @Transactional
    public List<Stock> getOverseasVolumeRanking(String market, int size) {
        if (appKey == null || appKey.isBlank()) return fallbackVolumeStocks(market, size);
        ensureAccessToken();
        if (accessToken == null) return fallbackVolumeStocks(market, size);

        String exchCode = switch (market) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> "NAS";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76310010");
        headers.set("custtype", "P");

        String url = baseUrl + "/uapi/overseas-stock/v1/ranking/trade-vol"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&NDAY=0"
                + "&VOL_RANG=0"
                + "&KEYB=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output2");
                if (outputs == null) outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackVolumeStocks(market, size);

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> o : outputs) {
                    if (result.size() >= size) break;
                    ensureOverseasRankingStock(o, market);
                    Stock s = updateStockFromOverseasRanking(o, market);
                    if (s != null) result.add(s);
                }
                return topUpStocksByVolume(result, market, size);
            }
        } catch (Exception e) {
            log.warn("KIS 해외 거래량 랭킹 조회 실패 ({}): {}", market, e.getMessage());
        }
        return fallbackVolumeStocks(market, size);
    }

    // ── KIS 해외 거래량 급증 API ──

    @Transactional
    public List<Stock> getOverseasVolumeSurge(String market, int size) {
        if (appKey == null || appKey.isBlank()) return Collections.emptyList();
        ensureAccessToken();
        if (accessToken == null) return Collections.emptyList();

        String exchCode = switch (market) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> "NAS";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS76270000");
        headers.set("custtype", "P");

        String url = baseUrl + "/uapi/overseas-stock/v1/ranking/volume-surge"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&MINX=8"        // 60일 거래량 필터 없음
                + "&VOL_RANG=0"
                + "&KEYB=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output2");
                if (outputs == null) outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return Collections.emptyList();

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> o : outputs) {
                    if (result.size() >= size) break;
                    ensureOverseasRankingStock(o, market);
                    Stock s = updateStockFromOverseasRanking(o, market);
                    if (s != null) result.add(s);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("KIS 해외 거래량 급증 랭킹 조회 실패 ({}): {}", market, e.getMessage());
        }
        return Collections.emptyList();
    }

    private Stock updateStockFromOverseasRanking(Map<String, Object> output, String market) {
        try {
            String code = String.valueOf(output.get("symb"));
            if (code == null || "null".equals(code)) return null;

            String lastStr = String.valueOf(output.get("last"));
            String rateStr = String.valueOf(output.get("rate"));
            String volStr = String.valueOf(output.get("tvol"));
            String tamtStr = String.valueOf(output.get("tamt"));
            boolean hasLast = lastStr != null && !"null".equals(lastStr) && !lastStr.isBlank() && !"0".equals(lastStr);
            boolean hasRate = rateStr != null && !"null".equals(rateStr) && !rateStr.isBlank();

            Optional<Stock> stockOpt = stockRepository.findByStockCode(code);
            if (stockOpt.isEmpty()) return null;

            Stock stock = stockOpt.get();
            if (hasLast)
                stock.setCurrentPrice(new BigDecimal(lastStr.trim()));
            if (hasRate)
                stock.setChangeRate(new BigDecimal(rateStr.trim()));
            if (volStr != null && !"null".equals(volStr) && !volStr.isBlank())
                stock.setTradingVolume(Long.parseLong(volStr.trim()));
            if (tamtStr != null && !"null".equals(tamtStr) && !tamtStr.isBlank())
                stock.setTradingValue(new BigDecimal(tamtStr.trim()));
            boolean needsQuoteRefresh = (!hasLast || !hasRate) && shouldRefreshOverseasQuote(stock);
            if (!needsQuoteRefresh) {
                stock.setPriceUpdatedAt(LocalDateTime.now());
            }
            stockRepository.save(stock);
            if (needsQuoteRefresh) {
                refreshOverseasPrice(stock);
            }
            return stock;
        } catch (Exception e) {
            log.debug("해외 랭킹 종목 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── 전일 거래량/거래대금 스냅샷 (종목별 전일 데이터 백업) ──

    @Transactional
    public List<Stock> getDomesticMarketCapRankingByMarket(String marketCode, int size) {
        return fetchDomesticMarketCapRanking(marketCode, size, true);
    }

    @Transactional
    public List<Stock> getCombinedDomesticMarketCapRanking(int size) {
        List<Stock> combined = new ArrayList<>();
        combined.addAll(fetchDomesticMarketCapRanking("KOSPI", size, false));
        combined.addAll(fetchDomesticMarketCapRanking("KOSDAQ", size, false));
        return sortStocksByMarketCap(combined, size);
    }

    @Transactional
    public List<Stock> getDomesticVolumeRankingByMarket(String marketCode, int size) {
        return fetchDomesticVolumeRanking(marketCode, size, true);
    }

    @Transactional
    public List<Stock> getCombinedDomesticVolumeRanking(int size) {
        List<Stock> combined = new ArrayList<>();
        combined.addAll(fetchDomesticVolumeRanking("KOSPI", size, false));
        combined.addAll(fetchDomesticVolumeRanking("KOSDAQ", size, false));
        return sortStocksByTradingVolume(combined, size);
    }

    @Transactional
    public List<Stock> getCombinedOverseasMarketCapRanking(int size) {
        List<Stock> combined = new ArrayList<>();
        combined.addAll(getOverseasMarketCapRanking("NASDAQ", size));
        combined.addAll(getOverseasMarketCapRanking("NYSE", size));
        combined.addAll(getOverseasMarketCapRanking("AMEX", size));
        return sortStocksByMarketCap(combined, size);
    }

    @Transactional
    public List<Stock> getCombinedOverseasVolumeRanking(int size) {
        List<Stock> combined = new ArrayList<>();
        combined.addAll(getOverseasVolumeRanking("NASDAQ", size));
        combined.addAll(getOverseasVolumeRanking("NYSE", size));
        combined.addAll(getOverseasVolumeRanking("AMEX", size));
        return sortStocksByTradingVolume(combined, size);
    }

    private List<Stock> fetchDomesticMarketCapRanking(String marketCode, int size, boolean fallbackToDb) {
        if (appKey == null || appKey.isBlank()) return fallbackToDb ? fallbackMarketCapStocks(marketCode, size) : Collections.emptyList();
        ensureAccessToken();
        if (accessToken == null) return fallbackToDb ? fallbackMarketCapStocks(marketCode, size) : Collections.emptyList();

        String iscd = "KOSDAQ".equals(marketCode) ? "1001" : "0001";

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01740000");
        headers.set("custtype", "P");

        String exclCode = "KOSDAQ".equals(marketCode) ? "0" : "0000000110";

        String url = baseUrl + "/uapi/domestic-stock/v1/ranking/market-cap"
                + "?fid_cond_mrkt_div_code=J"
                + "&fid_cond_scr_div_code=20174"
                + "&fid_input_iscd=" + iscd
                + "&fid_div_cls_code=1"
                + "&fid_trgt_cls_code=0"
                + "&fid_trgt_exls_cls_code=" + exclCode
                + "&fid_input_price_1="
                + "&fid_input_price_2="
                + "&fid_vol_cnt=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackToDb ? fallbackMarketCapStocks(marketCode, size) : Collections.emptyList();

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> output : outputs) {
                    ensureDomesticRankingStock(output, marketCode);
                    Stock stock = updateStockFromMarketCapRanking(output, marketCode);
                    if (stock != null) {
                        result.add(stock);
                    }
                }
                List<Stock> sorted = sortStocksByMarketCap(result, size);
                if (!fallbackToDb) {
                    return sorted;
                }
                return topUpStocksByMarketCap(sorted, marketCode, size);
            }
        } catch (Exception e) {
            log.warn("KIS 국내 시가총액 랭킹 조회 실패 ({}) : {}", marketCode, e.getMessage());
        }
        return fallbackToDb ? fallbackMarketCapStocks(marketCode, size) : Collections.emptyList();
    }

    private List<Stock> fetchDomesticVolumeRanking(String marketCode, int size, boolean fallbackToDb) {
        if (appKey == null || appKey.isBlank()) return fallbackToDb ? fallbackVolumeStocks(marketCode, size) : Collections.emptyList();
        ensureAccessToken();
        if (accessToken == null) return fallbackToDb ? fallbackVolumeStocks(marketCode, size) : Collections.emptyList();

        String iscd = "KOSDAQ".equals(marketCode) ? "1001" : "0001";

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01710000");
        headers.set("custtype", "P");

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/volume-rank"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_COND_SCR_DIV_CODE=20171"
                + "&FID_INPUT_ISCD=" + iscd
                + "&FID_DIV_CLS_CODE=0"
                + "&FID_BLNG_CLS_CODE=0"
                + "&FID_TRGT_CLS_CODE=111111111"
                + "&FID_TRGT_EXLS_CLS_CODE=000000"
                + "&FID_INPUT_PRICE_1="
                + "&FID_INPUT_PRICE_2="
                + "&FID_VOL_CNT="
                + "&FID_INPUT_DATE_1=";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.getBody().get("output");
                if (outputs == null) return fallbackToDb ? fallbackVolumeStocks(marketCode, size) : Collections.emptyList();

                List<Stock> result = new ArrayList<>();
                for (Map<String, Object> output : outputs) {
                    ensureDomesticRankingStock(output, marketCode);
                    Stock stock = updateStockFromVolumeRanking(output);
                    if (stock != null) {
                        result.add(stock);
                    }
                }
                List<Stock> sorted = sortStocksByTradingVolume(result, size);
                if (!fallbackToDb) {
                    return sorted;
                }
                return topUpStocksByVolume(sorted, marketCode, size);
            }
        } catch (Exception e) {
            log.warn("KIS 국내 거래량 랭킹 조회 실패 ({}) : {}", marketCode, e.getMessage());
        }
        return fallbackToDb ? fallbackVolumeStocks(marketCode, size) : Collections.emptyList();
    }

    private List<Stock> fallbackMarketCapStocks(String marketCode, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        if (marketCode == null || marketCode.isBlank()) {
            return stockRepository.findByMarketCapDescAndMarkets(List.of("KOSPI", "KOSDAQ"), pageable);
        }
        return stockRepository.findByMarketCapDescAndMarket(marketCode, pageable);
    }

    private List<Stock> fallbackVolumeStocks(String marketCode, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        if (marketCode == null || marketCode.isBlank()) {
            return stockRepository.findByTradingVolumeDescAndMarkets(List.of("KOSPI", "KOSDAQ"), pageable);
        }
        return stockRepository.findByTradingVolumeDescAndMarket(marketCode, pageable);
    }

    private List<Stock> sortStocksByMarketCap(List<Stock> stocks, int size) {
        return dedupeStocks(stocks).stream()
                .filter(stock -> stock.getMarketCap() != null)
                .sorted(Comparator.comparing(Stock::getMarketCap, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .limit(size)
                .toList();
    }

    private List<Stock> sortStocksByTradingVolume(List<Stock> stocks, int size) {
        return dedupeStocks(stocks).stream()
                .filter(stock -> stock.getTradingVolume() != null)
                .sorted(Comparator.comparing(Stock::getTradingVolume, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(size)
                .toList();
    }

    private List<Stock> dedupeStocks(List<Stock> stocks) {
        Map<String, Stock> deduped = new LinkedHashMap<>();
        for (Stock stock : stocks) {
            if (stock == null || stock.getStockCode() == null) continue;
            deduped.put(stock.getStockCode(), stock);
        }
        return new ArrayList<>(deduped.values());
    }
    @Scheduled(cron = "0 0 16 * * MON-FRI") // 평일 16시(장 마감 후 전일 데이터 백업)
    @Transactional
    public void backupPrevDayVolumes() {
        if (!backupLock.tryLock()) {
            log.warn("Another volume backup is already running, skipping");
            return;
        }
        try {
            List<Stock> stocks = stockRepository.findAll();
            int count = 0;
            for (Stock s : stocks) {
                if (s.getTradingVolume() != null) {
                    s.setPrevTradingVolume(s.getTradingVolume());
                    s.setPrevTradingValue(s.getTradingValue());
                    count++;
                }
            }
            stockRepository.saveAll(stocks);
            log.info("Previous-day volume backup complete: {} items", count);
        } finally {
            backupLock.unlock();
        }
    }

    // ── 뉴스 파이프라인용 후보 종목 수집 ──

    /**
     * 국내외 각종 API 랭킹에서 후보 종목 ~100~150개를 수집하여 반환.
     * 뉴스 파이프라인의 Layer 2 후보 종목풀로 사용.
     */
    @Transactional
    public List<Stock> collectCandidateStocks() {
        Map<Long, Stock> candidates = new LinkedHashMap<>();
        int perRanking = 30;

        // 1. 국내 시가총액 상위 종목
        for (Stock s : getCombinedDomesticMarketCapRanking(perRanking)) {
            candidates.putIfAbsent(s.getId(), s);
        }
        sleep60();

        // 2. 국내 상승률 상위 종목
        for (StockResponse sr : getDomesticRanking("KOSPI", false, perRanking)) {
            stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
        }
        sleep60();

        for (StockResponse sr : getDomesticRanking("KOSDAQ", false, perRanking)) {
            stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
        }
        sleep60();

        // 3. 국내 하락률 상위 종목
        for (StockResponse sr : getDomesticRanking("KOSPI", true, perRanking)) {
            stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
        }
        sleep60();

        for (StockResponse sr : getDomesticRanking("KOSDAQ", true, perRanking)) {
            stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
        }
        sleep60();

        // 4. 국내 거래량 상위 종목
        for (Stock s : getCombinedDomesticVolumeRanking(perRanking)) {
            candidates.putIfAbsent(s.getId(), s);
        }
        sleep60();

        // 5. 국내 거래량 급증 종목 (DB 기준 prevTradingVolume 대비 급증한 종목)
        for (Stock s : stockRepository.findByVolumeSurgeDesc(org.springframework.data.domain.PageRequest.of(0, perRanking))) {
            candidates.putIfAbsent(s.getId(), s);
        }

        // 6. 해외 (NASDAQ, NYSE, AMEX)
        for (String market : List.of("NASDAQ", "NYSE", "AMEX")) {
            for (Stock s : getOverseasMarketCapRanking(market, perRanking)) {
                candidates.putIfAbsent(s.getId(), s);
            }
            sleep60();

            for (StockResponse sr : getOverseasRanking(market, false, perRanking)) {
                stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
            }
            sleep60();

            for (StockResponse sr : getOverseasRanking(market, true, perRanking)) {
                stockRepository.findByStockCode(sr.getStockCode()).ifPresent(s -> candidates.putIfAbsent(s.getId(), s));
            }
            sleep60();

            for (Stock s : getOverseasVolumeRanking(market, perRanking)) {
                candidates.putIfAbsent(s.getId(), s);
            }
            sleep60();

            for (Stock s : getOverseasVolumeSurge(market, perRanking)) {
                candidates.putIfAbsent(s.getId(), s);
            }
            sleep60();
        }

        log.info("뉴스 파이프라인 후보 종목 수집 완료: {}개 (국내+해외 통합)", candidates.size());
        return new ArrayList<>(candidates.values());
    }

    private void sleep60() {
        try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── 개별 시세 조회 ──

    private void refreshPrice(Stock stock) {
        if ("KOSPI".equals(stock.getMarket()) || "KOSDAQ".equals(stock.getMarket())) {
            refreshDomesticPrice(stock);
        } else if ("NASDAQ".equals(stock.getMarket()) || "NYSE".equals(stock.getMarket()) || "AMEX".equals(stock.getMarket())) {
            refreshOverseasPrice(stock);
        }
    }

    public void refreshDomesticPrice(Stock stock) {
        ensureAccessToken();
        if (accessToken == null) return;

        String fid = "J";

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100");

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?FID_COND_MRKT_DIV_CODE=" + fid
                + "&FID_INPUT_ISCD=" + stock.getStockCode();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
                if (output != null) {
                    String priceStr = String.valueOf(output.get("stck_prpr"));
                    String changeRateStr = String.valueOf(output.get("prdy_ctrt"));
                    if (hasNumber(priceStr)) {
                        stock.setCurrentPrice(new BigDecimal(priceStr));
                    }
                    if (isDomesticTradingHoursNow() && hasNumber(changeRateStr)) {
                        stock.setChangeRate(new BigDecimal(changeRateStr));
                    }
                    stock.setPriceUpdatedAt(LocalDateTime.now());
                    stockRepository.save(stock);
                }
            }
        } catch (Exception e) {
            log.warn("KIS 국내 시세 조회 실패: {}", stock.getStockCode(), e);
        }
    }

    public void refreshOverseasPrice(Stock stock) {
        ensureAccessToken();
        if (accessToken == null) return;

        String exchCode = switch (stock.getMarket()) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> null;
        };
        if (exchCode == null) return;

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "HHDFS00000300");

        String url = baseUrl + "/uapi/overseas-price/v1/quotations/price"
                + "?AUTH="
                + "&EXCD=" + exchCode
                + "&SYMB=" + stock.getStockCode();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
                if (output == null || output.isEmpty()) return;

                String lastStr = String.valueOf(output.get("last"));
                String rateStr = String.valueOf(output.get("rate"));

                if (lastStr != null && !lastStr.isBlank() && !"null".equals(lastStr) && !"0".equals(lastStr)) {
                    stock.setCurrentPrice(new BigDecimal(lastStr.trim()));
                }
                if (rateStr != null && !rateStr.isBlank() && !"null".equals(rateStr)) {
                    stock.setChangeRate(new BigDecimal(rateStr.trim()));
                }
                stock.setPriceUpdatedAt(LocalDateTime.now());
                stockRepository.save(stock);
            }
        } catch (Exception e) {
            log.warn("KIS 해외 시세 조회 실패: {} ({})", stock.getStockCode(), stock.getMarket(), e.getMessage());
        }
    }

    @Transactional
    public void refreshOverseasQuotes(List<Long> stockIds) {
        if (stockIds == null || stockIds.isEmpty()) {
            return;
        }

        ensureAccessToken();
        if (accessToken == null) {
            return;
        }

        List<Stock> stocks = stockRepository.findAllById(stockIds);
        for (Stock stock : stocks) {
            if (!isOverseasMarket(stock.getMarket()) || !stock.isActive() || stock.isEtf()) {
                continue;
            }
            try {
                refreshOverseasPrice(stock);
                Thread.sleep(40);
            } catch (Exception e) {
                log.debug("overseas quote backfill skipped: {} ({})", stock.getStockCode(), e.getMessage());
            }
        }
    }

    private boolean shouldFallbackDomesticRanking(String marketCode, List<StockResponse> ranked) {
        if (ranked.isEmpty()) return true;
        return "KOSDAQ".equals(marketCode) && ranked.size() <= 1;
    }

    private List<StockResponse> fallbackRankingResponses(String market, boolean ascending, int size) {
        var pageable = PageRequest.of(0, size);
        List<Stock> stocks;

        if (market == null || market.isBlank()) {
            stocks = ascending
                    ? stockRepository.findByChangeRateAsc(pageable)
                    : stockRepository.findByChangeRateDesc(pageable);
        } else {
            stocks = ascending
                    ? stockRepository.findByChangeRateAscAndMarket(market, pageable)
                    : stockRepository.findByChangeRateDescAndMarket(market, pageable);
        }

        return stocks.stream()
                .map(this::toRankingResponse)
                .collect(Collectors.toList());
    }

    private List<StockResponse> normalizeRankingResponses(List<StockResponse> ranked, boolean ascending, int size) {
        if (ranked.isEmpty()) return List.of();

        List<StockResponse> directional = ranked.stream()
                .filter(stock -> stock.getChangeRate() != null)
                .filter(stock -> ascending
                        ? stock.getChangeRate().compareTo(BigDecimal.ZERO) <= 0
                        : stock.getChangeRate().compareTo(BigDecimal.ZERO) >= 0)
                .collect(Collectors.toList());

        List<StockResponse> source = directional.isEmpty() ? ranked : directional;
        Comparator<StockResponse> comparator = Comparator
                .comparing(StockResponse::getChangeRate, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(StockResponse::getStockName, Comparator.nullsLast(String::compareToIgnoreCase));

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return source.stream()
                .sorted(comparator)
                .limit(size)
                .collect(Collectors.toList());
    }

    private List<StockResponse> mergeRankings(List<List<StockResponse>> rankings, boolean ascending, int size) {
        Map<String, StockResponse> merged = new LinkedHashMap<>();
        for (List<StockResponse> ranking : rankings) {
            for (StockResponse stock : ranking) {
                if (stock == null || stock.getStockCode() == null) continue;
                merged.putIfAbsent(stock.getStockCode(), stock);
            }
        }
        return normalizeRankingResponses(new ArrayList<>(merged.values()), ascending, size);
    }

    private List<StockResponse> topUpRankingResponses(List<StockResponse> current, String market, boolean ascending, int size) {
        if (current.size() >= size) return current;

        Map<String, StockResponse> merged = new LinkedHashMap<>();
        for (StockResponse stock : current) {
            if (stock == null || stock.getStockCode() == null) continue;
            merged.putIfAbsent(stock.getStockCode(), stock);
        }
        for (StockResponse stock : fallbackRankingResponses(market, ascending, size)) {
            if (stock == null || stock.getStockCode() == null) continue;
            merged.putIfAbsent(stock.getStockCode(), stock);
        }
        return normalizeRankingResponses(new ArrayList<>(merged.values()), ascending, size);
    }

    private void ensureDomesticRankingStock(Map<String, Object> output, String market) {
        String code = sanitizeString(output.get("stck_shrn_iscd"));
        if (code == null) {
            code = sanitizeString(output.get("mksc_shrn_iscd"));
        }
        if (code == null) return;
        String name = sanitizeString(output.get("hts_kor_isnm"));
        if (name == null) {
            name = sanitizeString(output.get("mksc_kor_isnm"));
        }
        findOrCreateStock(code, name, market);
    }

    private void ensureOverseasRankingStock(Map<String, Object> output, String market) {
        String code = sanitizeString(output.get("symb"));
        if (code == null) {
            code = sanitizeString(output.get("mksc_shrn_iscd"));
        }
        if (code == null) return;
        String name = sanitizeString(output.get("name"));
        if (name == null) {
            name = sanitizeString(output.get("knam"));
        }
        if (name == null) {
            name = sanitizeString(output.get("hts_kor_isnm"));
        }
        findOrCreateStock(code, name, market);
    }

    private Stock findOrCreateStock(String code, String name, String market) {
        Optional<Stock> existing = stockRepository.findByStockCode(code);
        Stock stock = existing.orElseGet(() -> Stock.builder()
                .stockCode(code)
                .stockName(name != null ? name : code)
                .market(market)
                .active(true)
                .build());
        if (name != null && !name.equals(stock.getStockName())) {
            stock.setStockName(name);
        }
        if ((stock.getMarket() == null || stock.getMarket().isBlank()) && market != null && !market.isBlank()) {
            stock.setMarket(market);
        }
        if (!stock.isActive()) {
            stock.setActive(true);
        }
        if (existing.isEmpty()) {
            stockRepository.save(stock);
        }
        return stock;
    }

    private String sanitizeString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private StockResponse toRankingResponse(Stock stock) {
        return StockResponse.builder()
                .id(stock.getId())
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .market(stock.getMarket())
                .currentPrice(stock.getCurrentPrice())
                .changeRate(stock.getChangeRate())
                .priceUpdatedAt(stock.getPriceUpdatedAt())
                .newsCount(0)
                .threadCount(0)
                .build();
    }

    private boolean hasNumber(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private List<Stock> topUpStocksByMarketCap(List<Stock> current, String marketCode, int size) {
        if (current.size() >= size) return current;
        List<Stock> merged = new ArrayList<>(current);
        merged.addAll(fallbackMarketCapStocks(marketCode, size));
        return sortStocksByMarketCap(merged, size);
    }

    private List<Stock> topUpStocksByVolume(List<Stock> current, String marketCode, int size) {
        if (current.size() >= size) return current;
        List<Stock> merged = new ArrayList<>(current);
        merged.addAll(fallbackVolumeStocks(marketCode, size));
        return sortStocksByTradingVolume(merged, size);
    }

    private boolean isDomesticTradingHoursNow() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(9, 0)) && !time.isAfter(LocalTime.of(15, 30));
    }

    private boolean isOverseasMarket(String market) {
        return "NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market);
    }

    private void ensureAccessToken() {
        if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return;
        }

        try {
            String url = baseUrl + "/oauth2/tokenP";
            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", appKey,
                    "appsecret", appSecret
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                accessToken = (String) response.getBody().get("access_token");
                tokenExpiry = LocalDateTime.now().plusHours(23);
                log.info("KIS access token issued successfully");
            }
        } catch (Exception e) {
            log.error("KIS 액세스 토큰 발급 실패", e);
            accessToken = null;
        }
    }
}
