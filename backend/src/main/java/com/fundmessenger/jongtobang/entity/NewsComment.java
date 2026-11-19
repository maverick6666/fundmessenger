package com.fundmessenger.jongtobang.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_comments", indexes = {
    @Index(name = "idx_news_comment_coupling", columnList = "coupling_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 댓글은 StockNewsCoupling(종목-뉴스 연결)에 붙는다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupling_id", nullable = false)
    private StockNewsCoupling coupling;

    /** 대댓글: 부모 댓글 (null이면 최상위, depth-1만 허용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private NewsComment parentComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    /** AI 응답 대기 중 */
    @Column(nullable = false)
    @Builder.Default
    private boolean needsAiResponse = false;

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
