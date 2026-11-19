package com.fundmessenger.club.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CreditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditType type;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CreditType {
        GRANT,      // SUPER_ADMIN → 동아리 지급
        REVOKE,     // SUPER_ADMIN → 동아리 차감
        ALLOCATE,   // 동아리 → 팀 분배
        RECLAIM,    // 팀 → 동아리 회수
        DEDUCT      // AI 사용 등 차감 (향후)
    }
}
