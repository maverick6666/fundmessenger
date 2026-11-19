package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "attention_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AttentionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(length = 20)
    private String formulaVersion;

    private BigDecimal topHype1;
    private BigDecimal topHype2;
    private BigDecimal topHype3;
    private BigDecimal avgHype;

    private Integer recentThreadCount7d;
    private Integer recentCommentCount7d;

    private BigDecimal normThread;
    private BigDecimal normComment;
    private BigDecimal attention;
}
