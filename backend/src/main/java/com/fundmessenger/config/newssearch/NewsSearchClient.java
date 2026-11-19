package com.fundmessenger.config.newssearch;

import java.util.List;

/**
 * 뉴스 검색 provider 인터페이스.
 */
public interface NewsSearchClient {
    NewsSearchProvider provider();

    /** 키워드 검색 */
    List<NewsSearchResult> search(String query, int maxResults);

    /** 벌크 수집 (MarketAux 등 페이지네이션 방식) */
    default List<NewsSearchResult> collect(int maxPages) {
        return List.of();
    }

    /** 이 provider가 사용 가능한지 (키 설정 여부 + 할당량 잔여) */
    boolean isAvailable();
}
