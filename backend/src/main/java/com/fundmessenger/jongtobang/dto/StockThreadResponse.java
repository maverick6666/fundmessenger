package com.fundmessenger.jongtobang.dto;

import com.fundmessenger.jongtobang.entity.StockThread;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockThreadResponse {
    private Long id;
    private Long stockId;
    private String stockCode;
    private String stockName;
    private String market;
    private String title;
    private String content;
    private Long authorMembershipId;
    private String authorName;
    private int likeCount;
    private int replyCount;
    private boolean liked; // 현재 사용자가 공감했는지
    private boolean aiGenerated;
    private LocalDateTime createdAt;

    public static StockThreadResponse from(StockThread thread, boolean liked) {
        return StockThreadResponse.builder()
                .id(thread.getId())
                .stockId(thread.getStock() != null ? thread.getStock().getId() : null)
                .stockCode(thread.getStockCode())
                .stockName(thread.getStockName())
                .market(thread.getMarket())
                .title(thread.getTitle())
                .content(thread.getContent())
                .authorMembershipId(thread.getAuthor().getId())
                .authorName(thread.getAuthor().getName())
                .likeCount(thread.getLikeCount())
                .replyCount(thread.getReplyCount())
                .liked(liked)
                .aiGenerated(thread.isAiGenerated())
                .createdAt(thread.getCreatedAt())
                .build();
    }
}
