package com.fundmessenger.club.entity;

import com.fundmessenger.board.entity.SemiCategory;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teams")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    @Builder.Default
    private Integer creditAllocation = 0;

    /** 팀 한줄 소개 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 기수 태그 누적 (팀 존속 이력) */
    @ManyToMany
    @JoinTable(
        name = "team_generations",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "semi_category_id")
    )
    @Builder.Default
    private Set<SemiCategory> generations = new HashSet<>();

    /** 팀 유형 (FUND, CUSTOM) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TeamType teamType = TeamType.CUSTOM;

    /** 시스템 팀 (회장단 등 — 사용자가 삭제 불가) */
    @Column(nullable = false)
    @Builder.Default
    private boolean systemTeam = false;

    /** soft delete */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
