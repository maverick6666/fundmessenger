package com.fundmessenger.config.newssearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 뉴스 검색 라우터 — 시장별 provider 체인 + fallback 오케스트레이터.
 *
 * 국내 종목 → Naver → SerpAPI(naver엔진)
 * 해외 종목 → Tavily(topic=finance) → MarketAux
 * 해외 뉴스 벌크 수집 → MarketAux → Tavily 보충
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSearchRouter {

    private final NaverSearchClient naverClient;
    private final TavilySearchClient tavilyClient;
    private final SerpApiSearchClient serpApiClient;
    private final MarketAuxSearchClient marketAuxClient;
    private final DailyQuotaTracker quotaTracker;

    @PostConstruct
    public void init() {
        log.info("=== NewsSearchRouter 초기화: Naver={}, Tavily={}, SerpAPI={}, MarketAux={} ===",
                naverClient.isAvailable() ? "ON" : "OFF",
                tavilyClient.isAvailable() ? "ON" : "OFF",
                serpApiClient.isAvailable() ? "ON" : "OFF",
                marketAuxClient.isAvailable() ? "ON" : "OFF");
    }

    /**
     * 종목 뉴스 검색 — 시장에 따라 provider 체인 선택.
     * SurgeReasonService, 동적 쿼리 등에서 사용.
     */
    public List<NewsSearchResult> searchStockNews(String query, String market, int maxResults) {
        List<NewsSearchClient> chain = isDomestic(market)
                ? List.of(naverClient, serpApiClient)   // 국내: Naver → SerpAPI
                : List.of(tavilyClient, marketAuxClient); // 해외: Tavily → MarketAux

        for (NewsSearchClient client : chain) {
            if (!client.isAvailable()) continue;

            List<NewsSearchResult> results = client.search(query, maxResults);
            if (!results.isEmpty()) {
                log.info("종목 뉴스 검색: query={}, provider={}, {}건",
                        query, client.provider(), results.size());
                return results;
            }
        }

        log.warn("종목 뉴스 검색 실패 (모든 provider 소진): query={}, market={}", query, market);
        return List.of();
    }

    /**
     * 국내 뉴스 정기 수집 — Naver 사용 (25,000/일).
     */
    public List<NewsSearchResult> collectDomesticNews(List<String> queries, int resultsPerQuery) {
        List<NewsSearchResult> all = new ArrayList<>();
        for (String query : queries) {
            List<NewsSearchResult> results = naverClient.search(query, resultsPerQuery);
            all.addAll(results);
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return all;
    }

    /** Tavily 해외 수집용 섹터별 쿼리 */
    private static final List<String> OVERSEAS_QUERIES = List.of(
            "stock market today earnings",
            "S&P 500 Nasdaq Dow Jones",
            "semiconductor AI Nvidia AMD",
            "Tesla EV battery lithium",
            "Federal Reserve interest rate bond",
            "biotech pharma FDA approval",
            "oil gold commodity prices",
            "IPO SPAC merger acquisition",
            "crypto bitcoin ethereum",
            "Apple Microsoft Google earnings"
    );

    /**
     * 해외 뉴스 정기 수집 — MarketAux 벌크 + Tavily 다중 쿼리 병행.
     */
    public List<NewsSearchResult> collectOverseasNews(int maxPages) {
        List<NewsSearchResult> results = new ArrayList<>();

        // 1차: MarketAux 벌크 수집
        if (marketAuxClient.isAvailable()) {
            results.addAll(marketAuxClient.collect(maxPages));
            if (!results.isEmpty()) {
                log.info("MarketAux 벌크 수집: {}건", results.size());
            }
        }

        // 2차: Tavily 다중 쿼리 수집 (MarketAux와 병행, 실패해도 독립 수집)
        if (tavilyClient.isAvailable()) {
            int tavilyTotal = 0;
            for (String query : OVERSEAS_QUERIES) {
                List<NewsSearchResult> tavilyResults = tavilyClient.search(query, 20);
                results.addAll(tavilyResults);
                tavilyTotal += tavilyResults.size();

                if (!tavilyClient.isAvailable()) {
                    log.info("Tavily 일일 한도 도달, 수집 중단");
                    break;
                }
                try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (tavilyTotal > 0) {
                log.info("Tavily 해외 뉴스 수집: {}건 ({}개 쿼리)", tavilyTotal, OVERSEAS_QUERIES.size());
            }
        }

        return results;
    }

    /**
     * 급등 종목 동적 검색 — 종목명 + 시장 기반.
     */
    public List<NewsSearchResult> searchSurgeReason(String stockName, String stockCode, String market, int maxResults) {
        if (isDomestic(market)) {
            // 국내: "종목명 주가" 검색
            return searchStockNews(stockName + " 주가", market, maxResults);
        } else {
            // 해외: 종목코드 + 종목명으로 영문 검색
            String query = stockCode + " " + stockName + " stock price surge";
            return searchStockNews(query, market, maxResults);
        }
    }

    /** 할당량 현황 (모니터링 API용) */
    public Map<String, Object> getQuotaStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("quota", quotaTracker.getUsageReport());
        status.put("providers", Map.of(
                "naver", naverClient.isAvailable(),
                "tavily", tavilyClient.isAvailable(),
                "serpapi", serpApiClient.isAvailable(),
                "marketaux", marketAuxClient.isAvailable()
        ));
        return status;
    }

    private boolean isDomestic(String market) {
        return "KOSPI".equals(market) || "KOSDAQ".equals(market);
    }
}
