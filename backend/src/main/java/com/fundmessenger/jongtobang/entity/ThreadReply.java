package com.fundmessenger.jongtobang.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "thread_replies", indexes = {
    @Index(name = "idx_reply_thread_created", columnList = "thread_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private StockThread thread;

    /** 대댓글: 부모 답글 (null이면 최상위 답글, depth-1만 허용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_reply_id")
    private ThreadReply parentReply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

    /** 답글 내용 */
    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    /** AI 응답 대기 중 (사람이 AI 콘텐츠에 답글 달면 true → AI 답변 후 false) */
    @Column(nullable = false)
    @Builder.Default
    private boolean needsAiResponse = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
