package com.fundmessenger.jongtobang.dto;

import com.fundmessenger.jongtobang.entity.StockNewsCoupling;
import lombok.Builder;
import lombok.Getter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NewsResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long couplingId;
    private Long canonicalNewsId;
    private String title;
    private List<String> tags;
    private int rawNewsCount;
    private int commentCount;
    private int likeCount;
    private boolean liked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 감성 분석
    private String sentimentType;
    private Integer sentimentScore;

    // 페르소나
    private String authorName;
    private String authorEmoji;

    // 펼친 상태용 (상세 조회 시)
    private String bodyMarkdown;
    private String updateLog;

    public static NewsResponse fromSummary(StockNewsCoupling coupling) {
        return fromSummary(coupling, false);
    }

    public static NewsResponse fromSummary(StockNewsCoupling coupling, boolean liked) {
        return NewsResponse.builder()
                .couplingId(coupling.getId())
                .canonicalNewsId(coupling.getCanonicalNews().getId())
                .title(coupling.getCanonicalNews().getTitle())
                .tags(parseTags(coupling.getCanonicalNews().getTagsJson()))
                .rawNewsCount(coupling.getCanonicalNews().getRawNewsCount())
                .commentCount(coupling.getCommentCount())
                .likeCount(coupling.getLikeCount())
                .liked(liked)
                .createdAt(coupling.getCanonicalNews().getCreatedAt())
                .updatedAt(coupling.getCanonicalNews().getUpdatedAt())
                .sentimentType(coupling.getCanonicalNews().getSentimentType())
                .sentimentScore(coupling.getCanonicalNews().getSentimentScore())
                .authorName(coupling.getCanonicalNews().getAuthorName())
                .authorEmoji(coupling.getCanonicalNews().getAuthorEmoji())
                .build();
    }

    public static NewsResponse fromDetail(StockNewsCoupling coupling) {
        return fromDetail(coupling, false);
    }

    public static NewsResponse fromDetail(StockNewsCoupling coupling, boolean liked) {
        return NewsResponse.builder()
                .couplingId(coupling.getId())
                .canonicalNewsId(coupling.getCanonicalNews().getId())
                .title(coupling.getCanonicalNews().getTitle())
                .tags(parseTags(coupling.getCanonicalNews().getTagsJson()))
                .rawNewsCount(coupling.getCanonicalNews().getRawNewsCount())
                .commentCount(coupling.getCommentCount())
                .likeCount(coupling.getLikeCount())
                .liked(liked)
                .createdAt(coupling.getCanonicalNews().getCreatedAt())
                .updatedAt(coupling.getCanonicalNews().getUpdatedAt())
                .sentimentType(coupling.getCanonicalNews().getSentimentType())
                .sentimentScore(coupling.getCanonicalNews().getSentimentScore())
                .authorName(coupling.getCanonicalNews().getAuthorName())
                .authorEmoji(coupling.getCanonicalNews().getAuthorEmoji())
                .bodyMarkdown(coupling.getCanonicalNews().getBodyMarkdown())
                .updateLog(coupling.getCanonicalNews().getUpdateLog())
                .build();
    }

    private static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
