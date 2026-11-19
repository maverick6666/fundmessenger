package com.fundmessenger.config.newssearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class NaverSearchClient implements NewsSearchClient {

    @Value("${naver.search.client-id:}")
    private String clientId;

    @Value("${naver.search.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public NaverSearchClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public NewsSearchProvider provider() {
        return NewsSearchProvider.NAVER;
    }

    @Override
    public boolean isAvailable() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public List<NewsSearchResult> search(String query, int maxResults) {
        if (!isAvailable()) return List.of();

        List<NewsSearchResult> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);

            String url = "https://openapi.naver.com/v1/search/news.json"
                    + "?query=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + "&display=" + Math.min(maxResults, 100)
                    + "&sort=date";

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return results;

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
            if (items == null) return results;

            for (Map<String, Object> item : items) {
                String title = cleanHtml((String) item.get("title"));
                String desc = cleanHtml((String) item.get("description"));
                String link = cleanHtml((String) item.get("originallink"));
                if (link == null || link.isBlank()) link = cleanHtml((String) item.get("link"));
                if (link == null || link.isBlank()) continue;

                results.add(new NewsSearchResult(
                        title, desc, null, link,
                        extractPublisher(link),
                        parseNaverDate((String) item.get("pubDate")),
                        NewsSearchProvider.NAVER
                ));
            }
        } catch (Exception e) {
            log.warn("Naver 검색 실패 (query={}): {}", query, e.getMessage());
        }
        return results;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&").replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'").replaceAll("&nbsp;", " ")
                .trim();
    }

    private String extractPublisher(String url) {
        if (url == null) return "unknown";
        try {
            return url.replaceFirst("https?://", "").split("/")[0]
                    .replaceFirst("^(www|news|m)\\.", "");
        } catch (Exception e) { return "unknown"; }
    }

    private LocalDateTime parseNaverDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (Exception e) { return LocalDateTime.now(); }
    }
}
