package com.fundmessenger.chat.repository;

import com.fundmessenger.chat.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    Optional<ChatMember> findByChatRoomIdAndMemberId(Long chatRoomId, Long memberId);

    List<ChatMember> findByChatRoomIdAndIsLeftFalse(Long chatRoomId);

    long countByChatRoomIdAndIsLeftFalse(Long chatRoomId);

    boolean existsByChatRoomIdAndMemberIdAndIsLeftFalse(Long chatRoomId, Long memberId);

    @Query("SELECT cm FROM ChatMember cm WHERE cm.member.id = :membershipId AND cm.isLeft = false")
    List<ChatMember> findActiveMembershipsByMember(Long membershipId);

    /** userId 기준으로 채팅방 활성 멤버 여부 확인 (WebSocket 구독 인가용) */
    @Query("SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END " +
           "FROM ChatMember cm WHERE cm.chatRoom.id = :roomId " +
           "AND cm.member.user.id = :userId AND cm.isLeft = false")
    boolean existsByRoomIdAndUserIdActive(Long roomId, Long userId);

    /** muted 상태 일괄 조회 */
    @Query("SELECT cm FROM ChatMember cm WHERE cm.member.id = :membershipId AND cm.chatRoom.id IN :roomIds AND cm.isLeft = false")
    List<ChatMember> findByMemberIdAndChatRoomIdIn(Long membershipId, List<Long> roomIds);

    /** 미읽음 메시지 수 (lastReadAt 이후 메시지 수) */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId " +
           "AND m.createdAt > COALESCE(:lastReadAt, CAST('1970-01-01T00:00:00' AS TIMESTAMP)) " +
           "AND m.sender.id <> :membershipId")
    long countUnread(Long roomId, java.time.LocalDateTime lastReadAt, Long membershipId);
}
