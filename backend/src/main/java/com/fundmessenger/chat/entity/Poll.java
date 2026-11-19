package com.fundmessenger.chat.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "polls", indexes = {
    @Index(name = "idx_poll_club", columnList = "club_id"),
    @Index(name = "idx_poll_chatroom", columnList = "chat_room_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 200)
    private String question;

    /** JSON array of option strings: ["양양","가평","제천"] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String options;

    @Column(nullable = false)
    @Builder.Default
    private boolean multipleChoice = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean anonymous = false;

    private LocalDateTime deadline;

    @Column(nullable = false)
    @Builder.Default
    private boolean closed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private ClubMembership createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
