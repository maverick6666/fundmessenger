package com.fundmessenger.jongtobang.service;

import com.fundmessenger.config.newssearch.NewsSearchResult;
import com.fundmessenger.config.newssearch.NewsSearchRouter;
import com.fundmessenger.jongtobang.entity.RawNews;
import com.fundmessenger.jongtobang.repository.RawNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDateTime;

/**
 * 뉴스 수집 서비스 — NewsSearchRouter를 통해 통합 수집.
 * Naver(국내) + MarketAux/Tavily(해외)를 자동 라우팅.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectorService {

    private final RawNewsRepository rawNewsRepository;
    private final NewsSearchRouter searchRouter;
    private final com.fundmessenger.jongtobang.repository.StockRepository stockRepository;

    // 기본 쿼리 (고정)
    private static final List<String> BASE_QUERIES = List.of(
            "증시 코스피 코스닥",
            "반도체 AI 엔비디아",
            "삼성전자 실적",
            "배터리 전기차",
            "바이오 신약 임상",
            "금리 환율 달러",
            "부동산 건설",
            "IPO 상장"
    );

    /** 동적 쿼리로 추가할 변동률 상위 종목 수 */
    private static final int DYNAMIC_QUERY_TOP_N = 10;
    /** 동적 쿼리 대상 최소 변동률 (%) */
    private static final java.math.BigDecimal DYNAMIC_QUERY_MIN_RATE = new java.math.BigDecimal("5");

    /**
     * 글로벌 뉴스 수집
     */
    public int collectNews() {
        int total = 0;
        total += collectDomestic();
        total += collectOverseas();
        log.info("뉴스 수집 완료: 신규 {}건", total);
        return total;
    }

    private int collectDomestic() {
        // 기본 8개 쿼리 + 동적 쿼리 (변동률 상위 종목명)
        List<String> allQueries = new ArrayList<>(BASE_QUERIES);
        List<String> dynamicQueries = buildDynamicQueries();
        allQueries.addAll(dynamicQueries);

        if (!dynamicQueries.isEmpty()) {
            log.info("동적 쿼리 {}건 추가: {}", dynamicQueries.size(), String.join(", ", dynamicQueries));
        }

        List<NewsSearchResult> results = searchRouter.collectDomesticNews(allQueries, 100);
        int saved = saveResults(results);
        log.info("Naver 뉴스 수집: {}건 (쿼리 {}개: 기본 {}+동적 {})",
                saved, allQueries.size(), BASE_QUERIES.size(), dynamicQueries.size());
        return saved;
    }

    /**
     * 동적 쿼리 생성 — 변동률 상위 국내 종목명으로 검색 쿼리 자동 생성.
     * "예선테크 상한가" 같은 종목도 잡히게 됨.
     */
    private List<String> buildDynamicQueries() {
        try {
            var topGainers = stockRepository.findByChangeRateDesc(
                    org.springframework.data.domain.PageRequest.of(0, DYNAMIC_QUERY_TOP_N));

            return topGainers.stream()
                    .filter(s -> s.getChangeRate() != null
                            && s.getChangeRate().abs().compareTo(DYNAMIC_QUERY_MIN_RATE) >= 0
                            && ("KOSPI".equals(s.getMarket()) || "KOSDAQ".equals(s.getMarket())))
                    .map(s -> s.getStockName() + " 주가")
                    .toList();
        } catch (Exception e) {
            log.warn("동적 쿼리 생성 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private int collectOverseas() {
        List<NewsSearchResult> results = searchRouter.collectOverseasNews(90);
        int saved = saveResults(results);
        log.info("해외 뉴스 수집 (MarketAux+Tavily): {}건", saved);
        return saved;
    }

    /**
     * 검색 결과를 RawNews로 저장 (URL 중복 체크)
     */
    private int saveResults(List<NewsSearchResult> results) {
        int saved = 0;
        for (NewsSearchResult r : results) {
            if (r.url() == null || r.url().isBlank()) continue;
            if (rawNewsRepository.existsByOriginalLink(r.url())) continue;

            RawNews raw = RawNews.builder()
                    .sourceChannel(r.source().name())
                    .originalTitle(truncate(r.title(), 500))
                    .originalDescription(r.bestContent())
                    .originalLink(truncate(r.url(), 1000))
                    .publisher(r.publisher())
                    .publishedAt(r.publishedAt())
                    .processed(false)
                    .build();

            rawNewsRepository.save(raw);
            saved++;
        }
        return saved;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
