package com.fundmessenger.config.newssearch;

import java.time.LocalDateTime;

/**
 * 통합 뉴스 검색 결과 DTO.
 * 모든 provider의 응답을 이 형식으로 통일.
 */
public record NewsSearchResult(
        String title,
        String description,
        String fullContent,   // Tavily만 제공 (include_raw_content). 나머지는 null.
        String url,
        String publisher,
        LocalDateTime publishedAt,
        NewsSearchProvider source
) {
    /** fullContent가 있으면 사용, 없으면 description fallback */
    public String bestContent() {
        if (fullContent != null && !fullContent.isBlank()) return fullContent;
        return description != null ? description : "";
    }
}
