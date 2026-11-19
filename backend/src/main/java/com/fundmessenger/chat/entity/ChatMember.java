package com.fundmessenger.chat.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chat_room_id", "member_id"})
}, indexes = {
        @Index(name = "idx_chatmember_member", columnList = "member_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private ClubMembership member;

    private LocalDateTime lastReadAt;

    @Builder.Default
    private boolean isLeft = false;

    /** 알림 음소거 — true면 이 방의 알림을 받지 않음 */
    @Builder.Default
    private boolean muted = false;
}
