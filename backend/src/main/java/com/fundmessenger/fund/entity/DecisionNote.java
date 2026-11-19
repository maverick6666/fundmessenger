package com.fundmessenger.fund.entity;

import com.fundmessenger.board.entity.Post;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "decision_notes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DecisionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteType noteType;

    // ── 내보내기 ──

    @Column(nullable = false)
    @Builder.Default
    private boolean exportEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_post_id")
    private Post linkedPost;

    // ── AI 생성 메타 ──

    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    @Column(length = 50)
    private String aiSessionId;

    private Integer aiSessionIndex;

    private Long sourceChatRoomId;

    // ── 작성자 ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

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
