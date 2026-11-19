package com.fundmessenger.chat.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.TradeRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", indexes = {
        @Index(name = "idx_chatroom_club_type", columnList = "club_id, type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRoom {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private JoinMode joinMode = JoinMode.FREE;

    // ── DISCUSSION 타입 전용 FK ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_request_id")
    private TradeRequest tradeRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private ClubMembership createdBy;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
