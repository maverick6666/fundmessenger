package com.fundmessenger.chat.repository;

import com.fundmessenger.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    long countByChatRoomId(Long chatRoomId);

    /** cursor 기반 페이징: beforeId 이전 메시지 size개 (#9) */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.id < :beforeId ORDER BY m.id DESC")
    List<ChatMessage> findBeforeCursor(Long roomId, Long beforeId, Pageable pageable);

    /** 최신 메시지 size개 (초기 로드) */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.id DESC")
    List<ChatMessage> findLatest(Long roomId, Pageable pageable);

    /** AI 의사결정서: 방의 모든 텍스트 메시지를 시간순으로 조회 */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.chatRoom.id = :roomId AND m.content IS NOT NULL ORDER BY m.createdAt ASC")
    List<ChatMessage> findAllTextMessagesByRoomId(Long roomId);

    /** AI 의사결정서: 특정 시점 이후 메시지만 조회 (이전 AI 생성 후 새 토론분만) */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.chatRoom.id = :roomId AND m.content IS NOT NULL AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<ChatMessage> findTextMessagesSince(Long roomId, LocalDateTime since);
}
