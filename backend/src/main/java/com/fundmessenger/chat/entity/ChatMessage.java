package com.fundmessenger.chat.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chatmsg_room_created", columnList = "chat_room_id, created_at DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {

    public enum MessageType { TEXT, STICKER, IMAGE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private ClubMembership sender;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String imageUrl;

    /** 스티커 전송 시 스티커 이미지 URL */
    private String stickerUrl;

    /** 스티커 전송 시 스티커 ID (검증용) */
    @Column(name = "sticker_id")
    private Long stickerId;

    @Column(name = "shared_post_id")
    private Long sharedPostId;

    @Column(name = "shared_news_id")
    private Long sharedNewsId;

    /** 투표 생성 메시지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
