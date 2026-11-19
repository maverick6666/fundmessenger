package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "canonical_news", indexes = {
    @Index(name = "idx_canonical_created", columnList = "created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CanonicalNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    /** AI 재작성 markdown 본문 */
    @Column(columnDefinition = "TEXT")
    private String bodyMarkdown;

    /** 업데이트 이력 markdown */
    @Column(columnDefinition = "TEXT")
    private String updateLog;

    /** 이 canonical을 구성한 raw news 수 */
    @Column(nullable = false)
    @Builder.Default
    private int rawNewsCount = 0;

    /** 원문 발행처 다양성 (서로 다른 publisher 수) */
    @Column(nullable = false)
    @Builder.Default
    private int publisherDiversity = 0;

    /** 누적 업데이트 횟수 */
    @Column(nullable = false)
    @Builder.Default
    private int updateCount = 0;

    /** 태그 (주제 태그, JSON 배열로 저장) */
    @Column(columnDefinition = "TEXT")
    private String tagsJson;

    /** 감성 타입: "bullish", "bearish", "neutral" */
    @Column(length = 20)
    private String sentimentType;

    /** 감성 점수: 1-100 (neutral이면 null) */
    @Column
    private Integer sentimentScore;

    /** 페르소나 닉네임 (예: "시장돌이") */
    @Column(length = 50)
    private String authorName;

    /** 페르소나 이모지 (예: "📈") */
    @Column(length = 10)
    private String authorEmoji;

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
