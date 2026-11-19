package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "hype_score_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HypeScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupling_id")
    private StockNewsCoupling coupling;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_news_id")
    private CanonicalNews canonicalNews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(length = 20)
    private String formulaVersion;

    @Column(length = 40)
    private String promptVersion;

    // --- inputs ---
    private BigDecimal relevance;
    private Integer rawCount;
    private Integer publisherCount;
    private BigDecimal daysSinceUpdate;
    private Integer recentCommentCount7d;

    // --- intermediates ---
    private BigDecimal strength;
    private BigDecimal freshness;
    private BigDecimal reaction;
    private BigDecimal instantHype;

    // --- EMA params ---
    private BigDecimal previousHype;
    private BigDecimal alphaUsed;
    private BigDecimal hoursElapsed;
    private Boolean adaptiveTriggered;

    // --- output ---
    private BigDecimal storedHype;
}
