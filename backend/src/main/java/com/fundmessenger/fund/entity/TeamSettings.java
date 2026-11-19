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
@Table(name = "team_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TeamSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private Team team;

    @Column(precision = 20, scale = 0)
    @Builder.Default
    private BigDecimal initialCapitalKrw = BigDecimal.ZERO;

    @Column(precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal initialCapitalUsd = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> exchangeHistory;

    @Builder.Default
    private Integer aiDailyLimit = 3;

    @Builder.Default
    private Integer aiUsageCount = 0;

    private LocalDate aiUsageResetDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean profitPublic = false;

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
