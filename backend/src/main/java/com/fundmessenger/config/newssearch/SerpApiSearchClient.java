package com.fundmessenger.config.newssearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SerpAPI 클라이언트 — 네이버 뉴스 엔진 사용.
 * engine=naver, where=news로 한국어 뉴스 검색.
 * 월 250회 무료 (키당 ~4회/일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerpApiSearchClient implements NewsSearchClient {

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
        return NewsSearchProvider.SERPAPI;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getSerpapi();
        return config.keyCount() > 0 &&
                !quotaTracker.isProviderExhausted(NewsSearchProvider.SERPAPI, config.keyCount(), config.getDailyLimitPerKey());
    }

    @Override
    public List<NewsSearchResult> search(String query, int maxResults) {
        var config = properties.getSerpapi();
        if (config.keyCount() == 0) return List.of();

        for (int attempt = 0; attempt < config.keyCount(); attempt++) {
            int idx = keyIndex.getAndIncrement() % config.keyCount();
            if (!quotaTracker.tryConsume(NewsSearchProvider.SERPAPI, idx, config.getDailyLimitPerKey())) {
                continue;
            }

            String apiKey = config.getKeyList().get(idx);
            List<NewsSearchResult> results = callSerpApi(apiKey, query, maxResults);
            if (!results.isEmpty()) {
                log.info("SerpAPI 검색 완료: query={}, {}건 (key index={})", query, results.size(), idx);
                return results;
            }
        }

        log.debug("SerpAPI 전체 키 소진 또는 결과 없음: query={}", query);
        return List.of();
    }

    private List<NewsSearchResult> callSerpApi(String apiKey, String query, int maxResults) {
        try {
            String url = "https://serpapi.com/search"
                    + "?engine=naver"
                    + "&where=news"
                    + "&query=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + "&api_key=" + apiKey;

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return List.of();

            List<Map<String, Object>> newsResults = (List<Map<String, Object>>) response.getBody().get("news_results");
            if (newsResults == null) return List.of();

            List<NewsSearchResult> output = new ArrayList<>();
            for (Map<String, Object> r : newsResults) {
                if (output.size() >= maxResults) break;

                String title = (String) r.get("title");
                String snippet = (String) r.get("snippet");
                String link = (String) r.get("link");
                String source = (String) r.get("source");

                output.add(new NewsSearchResult(
                        title != null ? title : "",
                        snippet != null ? snippet : "",
                        null,  // SerpAPI는 본문 전문 미제공
                        link != null ? link : "",
                        source != null ? source : "unknown",
                        LocalDateTime.now(),
                        NewsSearchProvider.SERPAPI
                ));
            }
            return output;
        } catch (Exception e) {
            log.warn("SerpAPI 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
