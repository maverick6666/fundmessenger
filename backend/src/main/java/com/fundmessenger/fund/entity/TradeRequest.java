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
@Table(name = "trade_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private ClubMembership requester;

    // ── 매수 필드 ──

    @Column(length = 20)
    private String targetTicker;

    @Column(length = 20)
    private String targetMarket;

    @Column(length = 100)
    private String targetTickerName;

    @Column(precision = 20, scale = 8)
    private BigDecimal orderQuantity;

    @Column(precision = 20, scale = 4)
    private BigDecimal buyPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> buyOrders;

    @Column(precision = 10, scale = 4)
    private BigDecimal targetRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> takeProfitTargets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> stopLossTargets;

    // ── 매도 필드 ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Column(precision = 20, scale = 8)
    private BigDecimal sellQuantity;

    @Column(precision = 20, scale = 4)
    private BigDecimal sellPrice;

    @Column(columnDefinition = "text")
    private String sellReason;

    // ── 공통 ──

    @Column(columnDefinition = "text", nullable = false)
    private String memo;

    // ── 승인/거절 ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private ClubMembership approver;

    private LocalDateTime approvedAt;

    @Column(precision = 20, scale = 4)
    private BigDecimal executedPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal executedQuantity;

    private LocalDateTime executedAt;

    @Column(columnDefinition = "text")
    private String rejectionReason;

    // ── 시간 ──

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
