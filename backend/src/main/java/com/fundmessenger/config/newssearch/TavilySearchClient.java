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
 * Tavily Search API 클라이언트.
 * - topic=finance/news 지원
 * - include_raw_content=true로 기사 본문 전문 확보 가능
 * - 멀티키 라운드로빈 + 일일 한도 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TavilySearchClient implements NewsSearchClient {

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
        return NewsSearchProvider.TAVILY;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getTavily();
        return config.keyCount() > 0 &&
                !quotaTracker.isProviderExhausted(NewsSearchProvider.TAVILY, config.keyCount(), config.getDailyLimitPerKey());
    }

    @Override
    public List<NewsSearchResult> search(String query, int maxResults) {
        var config = properties.getTavily();
        if (config.keyCount() == 0) return List.of();

        // 라운드로빈으로 키 선택
        for (int attempt = 0; attempt < config.keyCount(); attempt++) {
            int idx = keyIndex.getAndIncrement() % config.keyCount();
            if (!quotaTracker.tryConsume(NewsSearchProvider.TAVILY, idx, config.getDailyLimitPerKey())) {
                continue; // 이 키 소진, 다음 키 시도
            }

            String apiKey = config.getKeyList().get(idx);
            List<NewsSearchResult> results = callTavily(apiKey, query, maxResults);
            if (!results.isEmpty()) {
                log.info("Tavily 검색 완료: query={}, {}건 (key index={})", query, results.size(), idx);
                return results;
            }
        }

        log.debug("Tavily 전체 키 소진 또는 결과 없음: query={}", query);
        return List.of();
    }

    private List<NewsSearchResult> callTavily(String apiKey, String query, int maxResults) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("topic", "finance");
            body.put("max_results", Math.min(maxResults, 20));
            body.put("include_raw_content", true);
            body.put("search_depth", "basic");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.tavily.com/search",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
            if (results == null) return List.of();

            List<NewsSearchResult> output = new ArrayList<>();
            for (Map<String, Object> r : results) {
                String title = (String) r.get("title");
                String content = (String) r.get("content");        // snippet
                String rawContent = (String) r.get("raw_content"); // 본문 전문
                String url = (String) r.get("url");
                String publishedDate = (String) r.get("published_date");

                output.add(new NewsSearchResult(
                        title != null ? title : "",
                        content != null ? content : "",
                        rawContent,   // Tavily만 본문 전문 제공
                        url != null ? url : "",
                        extractPublisher(url),
                        parseDate(publishedDate),
                        NewsSearchProvider.TAVILY
                ));
            }
            return output;
        } catch (Exception e) {
            log.warn("Tavily API 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractPublisher(String url) {
        if (url == null) return "unknown";
        try {
            return url.replaceFirst("https?://", "").split("/")[0]
                    .replaceFirst("^(www|news)\\.", "");
        } catch (Exception e) { return "unknown"; }
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return ZonedDateTime.parse(dateStr).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr.substring(0, Math.min(19, dateStr.length())));
            } catch (Exception e2) { return LocalDateTime.now(); }
        }
    }
}
