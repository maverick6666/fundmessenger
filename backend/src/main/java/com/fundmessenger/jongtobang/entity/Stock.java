package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"stock_code"})
       },
       indexes = {
           @Index(name = "idx_stock_market_active", columnList = "market, active")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    /** KOSPI / KOSDAQ / NASDAQ / NYSE / OTHER */
    @Column(length = 20)
    private String market;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal changeRate;

    /** 시가총액 (억원 / 백만달러) */
    @Column(precision = 20, scale = 0)
    private BigDecimal marketCap;

    /** 당일 누적 거래량 */
    private Long tradingVolume;

    /** 당일 누적 거래대금 (원 / 달러) */
    @Column(precision = 20, scale = 0)
    private BigDecimal tradingValue;

    /** 전일 거래량 (거래량 급증 계산용) */
    private Long prevTradingVolume;

    /** 전일 거래대금 (거래대금 급증 계산용) */
    @Column(precision = 20, scale = 0)
    private BigDecimal prevTradingValue;

    private LocalDateTime priceUpdatedAt;

    /** ETF 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean etf = false;

    /** 비활성(상장폐지) 시 false */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
