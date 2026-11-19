package com.fundmessenger.config.newssearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MarketAux API 클라이언트.
 * Free: 100 req/day, 3 articles/req → 최대 300건/일.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketAuxSearchClient implements NewsSearchClient {

    private final NewsSearchProperties properties;
    private final DailyQuotaTracker quotaTracker;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    @Override
    public NewsSearchProvider provider() {
        return NewsSearchProvider.MARKETAUX;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getMarketaux();
        return config.keyCount() > 0 &&
                !quotaTracker.isProviderExhausted(NewsSearchProvider.MARKETAUX, config.keyCount(), config.getDailyLimitPerKey());
    }

    @Override
    public List<NewsSearchResult> search(String query, int maxResults) {
        var config = properties.getMarketaux();
        if (config.keyCount() == 0) return List.of();

        for (int attempt = 0; attempt < config.keyCount(); attempt++) {
            int idx = keyIndex.getAndIncrement() % config.keyCount();
            if (!quotaTracker.tryConsume(NewsSearchProvider.MARKETAUX, idx, config.getDailyLimitPerKey())) {
                continue;
            }

            String apiKey = config.getKeyList().get(idx);
            List<NewsSearchResult> results = callMarketAux(apiKey, query, Math.min(maxResults, 3));
            if (!results.isEmpty()) return results;
        }
        return List.of();
    }

    @Override
    public List<NewsSearchResult> collect(int maxPages) {
        var config = properties.getMarketaux();
        if (config.keyCount() == 0) return List.of();

        List<NewsSearchResult> allResults = new ArrayList<>();
        int consecutiveEmpty = 0;

        for (int page = 1; page <= maxPages; page++) {
            // 키 순환
            int idx = keyIndex.getAndIncrement() % config.keyCount();
            if (!quotaTracker.tryConsume(NewsSearchProvider.MARKETAUX, idx, config.getDailyLimitPerKey())) {
                // 이 키 소진 → 다른 키 시도
                boolean found = false;
                for (int k = 0; k < config.keyCount(); k++) {
                    idx = (idx + 1) % config.keyCount();
                    if (quotaTracker.tryConsume(NewsSearchProvider.MARKETAUX, idx, config.getDailyLimitPerKey())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log.info("MarketAux 전체 키 일일 한도 도달, {}페이지에서 중단", page);
                    break;
                }
            }

            String apiKey = config.getKeyList().get(idx);
            List<NewsSearchResult> pageResults = callMarketAuxPage(apiKey, page);

            if (pageResults.isEmpty()) {
                consecutiveEmpty++;
                if (consecutiveEmpty >= 3) {
                    log.info("MarketAux 연속 {}페이지 빈 결과, 중단", consecutiveEmpty);
                    break;
                }
            } else {
                consecutiveEmpty = 0;
                allResults.addAll(pageResults);
            }

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        if (!allResults.isEmpty()) {
            log.info("MarketAux 수집 완료: {}건", allResults.size());
        }
        return allResults;
    }

    private List<NewsSearchResult> callMarketAux(String apiKey, String query, int limit) {
        try {
            String url = "https://api.marketaux.com/v1/news/all"
                    + "?api_token=" + apiKey
                    + "&search=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + "&language=en"
                    + "&must_have_entities=true"
                    + "&entity_types=equity"
                    + "&sort=published_at"
                    + "&sort_order=desc"
                    + "&limit=" + limit;

            return parseMarketAuxResponse(url);
        } catch (Exception e) {
            log.warn("MarketAux 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<NewsSearchResult> callMarketAuxPage(String apiKey, int page) {
        try {
            String url = "https://api.marketaux.com/v1/news/all"
                    + "?api_token=" + apiKey
                    + "&language=en"
                    + "&must_have_entities=true"
                    + "&entity_types=equity,index,etf"
                    + "&sort=published_at"
                    + "&sort_order=desc"
                    + "&limit=3"
                    + "&page=" + page;

            return parseMarketAuxResponse(url);
        } catch (Exception e) {
            log.warn("MarketAux page {} 실패: {}", page, e.getMessage());
            return List.of();
        }
    }

    private List<NewsSearchResult> parseMarketAuxResponse(String url) {
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return List.of();

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        if (data == null || data.isEmpty()) return List.of();

        List<NewsSearchResult> results = new ArrayList<>();
        for (Map<String, Object> article : data) {
            String articleUrl = (String) article.get("url");
            if (articleUrl == null || articleUrl.isBlank()) continue;

            String title = (String) article.get("title");
            String desc = (String) article.get("description");
            String snippet = (String) article.get("snippet");
            String source = (String) article.get("source");

            String finalDesc = (desc != null && !desc.isBlank()) ? desc : (snippet != null ? snippet : "");

            results.add(new NewsSearchResult(
                    title != null ? title : "",
                    finalDesc,
                    null,
                    articleUrl,
                    source != null ? source : "unknown",
                    parseDate((String) article.get("published_at")),
                    NewsSearchProvider.MARKETAUX
            ));
        }
        return results;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try { return ZonedDateTime.parse(dateStr).toLocalDateTime(); }
        catch (Exception e) { return LocalDateTime.now(); }
    }
}
