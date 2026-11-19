package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_news_couplings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"stock_id", "canonical_news_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StockNewsCoupling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_news_id", nullable = false)
    private CanonicalNews canonicalNews;

    /** AI가 부여한 관련도 (1~10) */
    @Column(nullable = false)
    @Builder.Default
    private int relevance = 5;

    /** 댓글 수 (캐시) */
    @Column(nullable = false)
    @Builder.Default
    private int commentCount = 0;

    /** 추천 수 (캐시) */
    @Column(nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
