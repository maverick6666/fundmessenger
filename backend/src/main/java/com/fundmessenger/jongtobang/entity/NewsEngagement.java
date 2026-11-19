package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_engagements")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** userId hash (privacy) */
    @Column(length = 64)
    private String userHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupling_id")
    private StockNewsCoupling coupling;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_news_id")
    private CanonicalNews canonicalNews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    /** impression / click / comment / dwell */
    @Column(length = 20, nullable = false)
    private String action;

    private Integer positionInList;
    private BigDecimal hypeAtTime;
    private BigDecimal attentionAtTime;
    private Integer dwellSeconds;
}
