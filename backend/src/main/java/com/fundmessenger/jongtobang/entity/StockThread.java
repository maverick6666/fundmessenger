package com.fundmessenger.jongtobang.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_threads", indexes = {
    @Index(name = "idx_thread_stock_created", columnList = "stock_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StockThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** Stock FK (새 컬럼 — 기존 stockCode/stockName/market 대체) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

    /** 종목 코드 (레거시 — stock_id 마이그레이션 전 호환용) */
    @Column(nullable = false, length = 20)
    private String stockCode;

    /** 종목명 (레거시) */
    @Column(nullable = false, length = 100)
    private String stockName;

    /** 시장 (레거시) */
    @Column(nullable = false, length = 20)
    private String market;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int replyCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
