package com.fundmessenger.chat.dto;

import com.fundmessenger.chat.entity.ChatRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class ChatRoomResponse {
    private Long id;
    private String type;
    private String name;
    private String description;
    private Integer maxMembers;
    private String joinMode;
    private Long teamId;
    private String teamName;
    private Long tradeRequestId;
    private Long positionId;
    private long memberCount;
    private long unreadCount;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private boolean muted;

    public static ChatRoomResponse from(ChatRoom room, long memberCount, long unreadCount,
                                         String lastMessage, LocalDateTime lastMessageAt) {
        return from(room, memberCount, unreadCount, lastMessage, lastMessageAt, false);
    }

    public static ChatRoomResponse from(ChatRoom room, long memberCount, long unreadCount,
                                         String lastMessage, LocalDateTime lastMessageAt, boolean muted) {
        return ChatRoomResponse.builder()
                .id(room.getId())
                .type(room.getType().name())
                .name(room.getName())
                .description(room.getDescription())
                .maxMembers(room.getMaxMembers())
                .joinMode(room.getJoinMode().name())
                .teamId(room.getTeam() != null ? room.getTeam().getId() : null)
                .teamName(room.getTeam() != null ? room.getTeam().getName() : null)
                .tradeRequestId(room.getTradeRequest() != null ? room.getTradeRequest().getId() : null)
                .positionId(room.getPosition() != null ? room.getPosition().getId() : null)
                .memberCount(memberCount)
                .unreadCount(unreadCount)
                .lastMessage(lastMessage)
                .lastMessageAt(lastMessageAt)
                .createdAt(room.getCreatedAt())
                .muted(muted)
                .build();
    }
}
