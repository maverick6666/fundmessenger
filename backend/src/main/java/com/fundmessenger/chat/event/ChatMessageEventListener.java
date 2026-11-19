package com.fundmessenger.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 후 WebSocket 브로드캐스트 (#3 트랜잭션 경계 해결)
 * - DB 커밋 전에 WebSocket 발송하면 클라이언트가 아직 없는 데이터를 조회할 수 있음
 * - AFTER_COMMIT으로 보장: DB에 메시지가 있는 상태에서만 클라이언트에 알림
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatMessageSent(ChatMessageSentEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/chat/" + event.getRoomId(),
                event.getMessage()
        );
    }
}
