package com.fundmessenger.chat.dto;

import com.fundmessenger.chat.entity.ChatMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class ChatMemberResponse {
    private Long id;
    private Long membershipId;
    private String name;
    private String avatarUrl;
    private LocalDateTime lastReadAt;

    public static ChatMemberResponse from(ChatMember cm) {
        return ChatMemberResponse.builder()
                .id(cm.getId())
                .membershipId(cm.getMember().getId())
                .name(cm.getMember().getName())
                .avatarUrl(cm.getMember().getUser() != null ? cm.getMember().getUser().getAvatarUrl() : null)
                .lastReadAt(cm.getLastReadAt())
                .build();
    }
}
