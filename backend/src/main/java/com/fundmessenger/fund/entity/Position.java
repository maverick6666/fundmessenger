package com.fundmessenger.fund.entity;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "positions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(length = 100)
    private String tickerName;

    @Column(nullable = false, length = 20)
    private String market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PositionStatus status = PositionStatus.OPEN;

    // ── 보유 정보 ──

    @Column(precision = 20, scale = 4)
    private BigDecimal averageBuyPrice;

    @Column(precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalBuyAmount = BigDecimal.ZERO;

    // ── 매매 계획 (JSONB) ──

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> buyPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> takeProfitTargets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> stopLossTargets;

    // ── 매도/실현 손익 ──

    @Column(precision = 20, scale = 4)
    private BigDecimal averageSellPrice;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalSellAmount;

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal realizedProfitLoss = BigDecimal.ZERO;

    @Column(precision = 10, scale = 4)
    private BigDecimal profitRate;

    private Long holdingPeriodHours;

    // ── 공개 설정 ──

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    private LocalDateTime publishedAt;

    private LocalDateTime lastExportedAt;

    // ── 수명주기 ──

    @Column(nullable = false)
    @Builder.Default
    private boolean infoConfirmed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opener_id")
    private ClubMembership opener;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closer_id")
    private ClubMembership closer;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.openedAt == null) {
            this.openedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
