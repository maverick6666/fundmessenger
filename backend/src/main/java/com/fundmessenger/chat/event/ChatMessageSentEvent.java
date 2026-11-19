package com.fundmessenger.chat.event;

import com.fundmessenger.chat.dto.ChatMessageResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageSentEvent {
    private final Long roomId;
    private final ChatMessageResponse message;
}
