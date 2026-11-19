package com.fundmessenger.jongtobang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.newssearch.NewsSearchResult;
import com.fundmessenger.config.newssearch.NewsSearchRouter;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 급등 종목 이유 검색 서비스.
 * "처음 보는 종목이 상한가를 찍으면, 왜 올랐는지 알 수 있어야 한다."
 *
 * NewsSearchRouter를 통해 시장별 최적 검색 API 자동 선택:
 * - 국내 급등 → Naver → SerpAPI fallback
 * - 해외 급등 → Tavily(본문 전문) → MarketAux fallback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeReasonService {

    private final StockRepository stockRepository;
    private final CanonicalNewsRepository canonicalNewsRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final StockNewsCouplingRepository couplingRepository;
    private final RawNewsRepository rawNewsRepository;
    private final CerebrasClient cerebrasClient;
    private final NewsSearchRouter searchRouter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final BigDecimal DOMESTIC_SURGE_THRESHOLD = new BigDecimal("15");
    private static final BigDecimal OVERSEAS_SURGE_THRESHOLD = new BigDecimal("10");
    private static final int RECENT_NEWS_HOURS = 24;
    private static final int MAX_SURGE_STOCKS_PER_RUN = 5;

    @Transactional
    public int runSurgeReasonPipeline() {
        long start = System.currentTimeMillis();
        log.info("=== 급등 종목 이유 검색 시작 ===");

        List<Stock> surgeStocks = detectSurgeStocks();
        if (surgeStocks.isEmpty()) {
            log.info("급등 종목 없음, 스킵");
            return 0;
        }
        log.info("급등 종목 감지: {}건", surgeStocks.size());

        List<Stock> uncoveredStocks = filterUncoveredStocks(surgeStocks);
        if (uncoveredStocks.isEmpty()) {
            log.info("모든 급등 종목에 이미 최근 뉴스 있음, 스킵");
            return 0;
        }
        log.info("뉴스 미커버 급등 종목: {}건 ({})", uncoveredStocks.size(),
                uncoveredStocks.stream().map(s -> s.getStockName() + " " + s.getChangeRate() + "%")
                        .collect(Collectors.joining(", ")));

        if (!cerebrasClient.isAvailable()) {
            log.warn("Cerebras API 미사용 가능, 급등 이유 검색 스킵");
            return 0;
        }

        int created = 0;
        for (Stock stock : uncoveredStocks) {
            try {
                boolean ok = searchAndCreateNews(stock);
                if (ok) created++;
            } catch (Exception e) {
                log.warn("급등 이유 검색 실패: {} ({})", stock.getStockName(), stock.getStockCode(), e);
            }
        }

        log.info("=== 급등 종목 이유 검색 완료: {}건 생성 ({}ms) ===", created, System.currentTimeMillis() - start);
        return created;
    }

    private List<Stock> detectSurgeStocks() {
        List<Stock> allStocks = stockRepository.findByChangeRateDesc(
                org.springframework.data.domain.PageRequest.of(0, 100));

        return allStocks.stream()
                .filter(s -> s.getChangeRate() != null && s.isActive() && !s.isEtf())
                .filter(s -> {
                    BigDecimal threshold = isDomestic(s) ? DOMESTIC_SURGE_THRESHOLD : OVERSEAS_SURGE_THRESHOLD;
                    return s.getChangeRate().compareTo(threshold) >= 0;
                })
                .limit(MAX_SURGE_STOCKS_PER_RUN)
                .toList();
    }

    private List<Stock> filterUncoveredStocks(List<Stock> surgeStocks) {
        LocalDateTime since = LocalDateTime.now().minusHours(RECENT_NEWS_HOURS);
        return surgeStocks.stream()
                .filter(stock -> {
                    List<StockNewsCoupling> recentCouplings = couplingRepository
                            .findByStockIdOrderByCreatedAtDesc(stock.getId());
                    return recentCouplings.stream()
                            .noneMatch(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since));
                })
                .toList();
    }

    private boolean searchAndCreateNews(Stock stock) {
        log.info("급등 이유 검색: {} ({}) +{}%",
                stock.getStockName(), stock.getStockCode(), stock.getChangeRate().toPlainString());

        // NewsSearchRouter가 시장에 따라 최적 API 자동 선택
        List<NewsSearchResult> searchResults = searchRouter.searchSurgeReason(
                stock.getStockName(), stock.getStockCode(), stock.getMarket(), 20);

        if (searchResults.isEmpty()) {
            log.info("검색 결과 없음: {}", stock.getStockName());
            return false;
        }
        log.info("검색 결과: {}건 (provider={}, {})",
                searchResults.size(), searchResults.get(0).source(), stock.getStockName());

        // 검색 결과를 RawNews로 저장
        List<RawNews> savedRaws = saveSearchResultsAsRaw(searchResults, stock);
        if (savedRaws.isEmpty()) {
            log.info("모든 검색 결과가 이미 수집된 기사: {}", stock.getStockName());
            return false;
        }

        return generateSurgeNews(stock, savedRaws, searchResults);
    }

    private List<RawNews> saveSearchResultsAsRaw(List<NewsSearchResult> results, Stock stock) {
        List<RawNews> saved = new ArrayList<>();
        for (NewsSearchResult r : results) {
            if (r.url() == null || r.url().isBlank()) continue;
            if (rawNewsRepository.existsByOriginalLink(r.url())) continue;

            RawNews raw = RawNews.builder()
                    .sourceChannel(r.source().name() + "_SURGE_SEARCH")
                    .originalTitle(truncate(r.title(), 500))
                    .originalDescription(r.bestContent())
                    .originalLink(truncate(r.url(), 1000))
                    .publisher(r.publisher())
                    .publishedAt(r.publishedAt())
                    .processed(true)
                    .build();

            rawNewsRepository.save(raw);
            saved.add(raw);
        }
        return saved;
    }

    private boolean generateSurgeNews(Stock stock, List<RawNews> rawNews, List<NewsSearchResult> searchResults) {
        // Tavily 본문 전문이 있으면 활용, 없으면 스니펫
        String rawTexts = searchResults.stream()
                .map(r -> r.title() + "\n" + r.bestContent())
                .collect(Collectors.joining("\n---\n"));

        String surgeDirection = stock.getChangeRate().compareTo(BigDecimal.ZERO) > 0 ? "급등" : "급락";
        String marketLabel = isDomestic(stock) ? "국내" : "해외";

        String systemPrompt = """
                너는 한국어로 쓰는 종목 뉴스 에디터다.
                **%s 종목이 %s했다. 왜 %s했는지 설명하는 기사를 작성하라.**

                종목 정보:
                - 종목명: %s
                - 종목코드: %s
                - 시장: %s
                - 현재가: %s
                - 변동률: %s%%
                - 거래량: %s

                아래에 검색으로 찾은 관련 기사들이 있다.
                이 기사들을 근거로 "왜 이 종목이 %s했는지"를 명확하게 설명하라.

                작성 원칙:
                - 출력은 반드시 한국어 JSON 하나만 반환한다.
                - 제목은 "종목명 + 왜 올랐나" 스타일로 직관적으로.
                - 본문은 투자자가 바로 이해할 수 있게 핵심부터 쓴다.
                - 검색 결과에서 급등 이유를 찾을 수 없으면, "현재 명확한 재료가 확인되지 않았다"고 솔직하게 쓴다.
                - 억지로 이유를 만들지 마라. 근거가 있는 것만 쓴다.
                - 차트 블록은 넣지 않는다.

                JSON 스키마:
                {
                  "title": "기사 제목",
                  "markdown": "한국어 markdown 본문",
                  "tags": ["태그1", "태그2"],
                  "sentiment": {"type": "<bullish|bearish|neutral>", "score": 75}
                }
                """.formatted(
                marketLabel, surgeDirection, surgeDirection,
                stock.getStockName(), stock.getStockCode(), stock.getMarket(),
                stock.getCurrentPrice() != null ? stock.getCurrentPrice().toPlainString() : "n/a",
                stock.getChangeRate().toPlainString(),
                stock.getTradingVolume() != null ? stock.getTradingVolume().toString() : "n/a",
                surgeDirection);

        String userPrompt = "=== 검색 결과 (종목: " + stock.getStockName() + ") ===\n" + rawTexts;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 4000);
        if (result == null) {
            log.error("급등 이유 뉴스 생성 실패 (API null): {} ({})", stock.getStockName(), stock.getStockCode());
            return false;
        }

        String tagsJson = null;
        JsonNode tagsNode = result.path("tags");
        if (tagsNode.isArray()) {
            try { tagsJson = objectMapper.writeValueAsString(tagsNode); } catch (Exception ignored) {}
        }

        String sentimentType = null;
        Integer sentimentScore = null;
        JsonNode sentimentNode = result.path("sentiment");
        if (!sentimentNode.isMissingNode()) {
            sentimentType = sentimentNode.path("type").asText(null);
            sentimentScore = sentimentNode.path("score").isNumber() ? sentimentNode.path("score").asInt() : null;
        }

        // 페르소나는 NewsPipelineService.pickNextPersona()와 동일 로직 사용하지 않고
        // 급등 뉴스는 "뉴스요정" 고정 (속보 성격)
        CanonicalNews canonical = CanonicalNews.builder()
                .title(result.path("title").asText(stock.getStockName() + " 급등 — 이유 분석"))
                .bodyMarkdown(result.path("markdown").asText(""))
                .rawNewsCount(rawNews.size())
                .publisherDiversity(calculatePublisherDiversity(rawNews))
                .updateCount(0)
                .tagsJson(tagsJson)
                .sentimentType(sentimentType)
                .sentimentScore(sentimentScore)
                .authorName("뉴스요정")
                .authorEmoji("🧚")
                .build();

        canonical = canonicalNewsRepository.save(canonical);

        NewsCluster cluster = NewsCluster.builder()
                .label("급등 이유: " + stock.getStockName() + " +" + stock.getChangeRate().toPlainString() + "%")
                .valid(true)
                .canonicalNews(canonical)
                .build();
        newsClusterRepository.save(cluster);

        for (RawNews raw : rawNews) {
            raw.setCluster(cluster);
        }
        rawNewsRepository.saveAll(rawNews);

        StockNewsCoupling coupling = StockNewsCoupling.builder()
                .stock(stock)
                .canonicalNews(canonical)
                .relevance(10)
                .build();
        couplingRepository.save(coupling);

        log.info("급등 이유 뉴스 생성: {} ({}) → id={}, title={}",
                stock.getStockName(), stock.getStockCode(), canonical.getId(), canonical.getTitle());
        return true;
    }

    private boolean isDomestic(Stock stock) {
        return "KOSPI".equals(stock.getMarket()) || "KOSDAQ".equals(stock.getMarket());
    }

    private int calculatePublisherDiversity(List<RawNews> rawNews) {
        return (int) rawNews.stream()
                .map(RawNews::getPublisher)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
