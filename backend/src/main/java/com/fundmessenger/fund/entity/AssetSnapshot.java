package com.fundmessenger.fund.entity;

import com.fundmessenger.club.entity.Team;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "asset_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "snapshot_date"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AssetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    // ── 현금 ──

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal krwCash = BigDecimal.ZERO;

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal krwEvaluation = BigDecimal.ZERO;

    @Column(precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal usdCash = BigDecimal.ZERO;

    @Column(precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal usdEvaluation = BigDecimal.ZERO;

    // ── 총 자산 ──

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalKrw = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ZERO;

    // ── 손익 ──

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    // ── 포지션 상세 (JSONB) ──

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> positionDetails;

    // ── 시간 ──

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
