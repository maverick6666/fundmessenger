package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_clusters")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AI가 부여한 클러스터 라벨 */
    @Column(length = 300)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_news_id")
    private CanonicalNews canonicalNews;

    /** 투자 맥락 유효성 (Layer 0에서 판단) */
    @Column(nullable = false)
    @Builder.Default
    private boolean valid = true;

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
