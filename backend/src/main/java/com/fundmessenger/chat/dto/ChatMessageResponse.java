package com.fundmessenger.chat.dto;

import com.fundmessenger.chat.entity.ChatMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class ChatMessageResponse {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String messageType;
    private String content;
    private String imageUrl;
    private String stickerUrl;
    private Long stickerId;
    private Long sharedPostId;
    private Long sharedNewsId;
    private String sharedType;
    private String sharedTitle;
    private String sharedTargetUrl;
    private Long pollId;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage msg) {
        return from(msg, null, null, null);
    }

    public static ChatMessageResponse from(ChatMessage msg, String sharedType, String sharedTitle, String sharedTargetUrl) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .chatRoomId(msg.getChatRoom().getId())
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getName())
                .senderAvatarUrl(msg.getSender().getUser() != null ? msg.getSender().getUser().getAvatarUrl() : null)
                .messageType(msg.getMessageType() != null ? msg.getMessageType().name() : "TEXT")
                .content(msg.getContent())
                .imageUrl(msg.getImageUrl())
                .stickerUrl(msg.getStickerUrl())
                .stickerId(msg.getStickerId())
                .sharedPostId(msg.getSharedPostId())
                .sharedNewsId(msg.getSharedNewsId())
                .sharedType(sharedType)
                .sharedTitle(sharedTitle)
                .sharedTargetUrl(sharedTargetUrl)
                .pollId(msg.getPoll() != null ? msg.getPoll().getId() : null)
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
