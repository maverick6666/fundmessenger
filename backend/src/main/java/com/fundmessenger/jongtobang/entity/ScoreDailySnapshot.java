package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "score_daily_snapshots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScoreDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    private Integer newsCount;
    private BigDecimal avgHype;
    private BigDecimal maxHype;
    private BigDecimal minHype;
    private BigDecimal attention;

    private Integer bucket0_20;
    private Integer bucket20_40;
    private Integer bucket40_60;
    private Integer bucket60_80;
    private Integer bucket80_100;
}
