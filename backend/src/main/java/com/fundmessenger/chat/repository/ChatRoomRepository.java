package com.fundmessenger.chat.repository;

import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByClubIdAndType(Long clubId, ChatRoomType type);

    Optional<ChatRoom> findByClubIdAndTeamIdAndType(Long clubId, Long teamId, ChatRoomType type);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.id IN " +
           "(SELECT cm.chatRoom.id FROM ChatMember cm WHERE cm.member.id = :membershipId AND cm.isLeft = false) " +
           "AND cr.club.id = :clubId ORDER BY cr.createdAt DESC")
    List<ChatRoom> findMyRooms(Long clubId, Long membershipId);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.club.id = :clubId AND cr.type = 'LIGHTNING' ORDER BY cr.createdAt DESC")
    List<ChatRoom> findLightningRooms(Long clubId);

    /** DM 방 찾기: 두 멤버가 모두 참여한 DM 방 */
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.club.id = :clubId AND cr.type = 'DM' " +
           "AND cr.id IN (SELECT cm1.chatRoom.id FROM ChatMember cm1 WHERE cm1.member.id = :m1 AND cm1.isLeft = false) " +
           "AND cr.id IN (SELECT cm2.chatRoom.id FROM ChatMember cm2 WHERE cm2.member.id = :m2 AND cm2.isLeft = false)")
    Optional<ChatRoom> findDmRoom(Long clubId, Long m1, Long m2);

    /** 종목 토론방 찾기: 같은 팀 + ticker 기준 기존 토론방 재사용 */
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.club.id = :clubId AND cr.type = 'DISCUSSION' " +
           "AND cr.team.id = :teamId AND cr.name = :name")
    Optional<ChatRoom> findDiscussionRoom(Long clubId, Long teamId, String name);

    /** TradeRequest 기반 토론방 조회 */
    Optional<ChatRoom> findByTradeRequestId(Long tradeRequestId);

    /**
     * 내 채팅방 + 멤버 수 (JOIN + GROUP BY, 서브쿼리 없음)
     * Object[]: [ChatRoom, memberCount]
     */
    @Query("""
        SELECT cr, COUNT(cm2)
        FROM ChatRoom cr
        JOIN ChatMember cm ON cm.chatRoom = cr AND cm.member.id = :membershipId AND cm.isLeft = false
        LEFT JOIN ChatMember cm2 ON cm2.chatRoom = cr AND cm2.isLeft = false
        WHERE cr.club.id = :clubId
        GROUP BY cr
        ORDER BY cr.type ASC, cr.createdAt DESC
        """)
    List<Object[]> findMyRoomsWithMemberCount(Long clubId, Long membershipId);

    /**
     * 방 목록의 마지막 메시지 정보 일괄 조회 (2 쿼리 → roomIds 전체)
     * Object[]: [roomId, lastMessageContent, lastMessageAt]
     */
    @Query("""
        SELECT m.chatRoom.id, m.content, m.createdAt
        FROM ChatMessage m
        WHERE m.chatRoom.id IN :roomIds
          AND m.createdAt = (SELECT MAX(m2.createdAt) FROM ChatMessage m2 WHERE m2.chatRoom.id = m.chatRoom.id)
        """)
    List<Object[]> findLastMessages(List<Long> roomIds);

    /**
     * 방 목록의 미읽음 수 일괄 조회
     * Object[]: [roomId, unreadCount]
     */
    @Query("""
        SELECT m.chatRoom.id, COUNT(m)
        FROM ChatMessage m
        JOIN ChatMember cm ON cm.chatRoom.id = m.chatRoom.id AND cm.member.id = :membershipId AND cm.isLeft = false
        WHERE m.chatRoom.id IN :roomIds
          AND m.createdAt > COALESCE(cm.lastReadAt, CAST('1970-01-01T00:00:00' AS TIMESTAMP))
          AND m.sender.id <> :membershipId
        GROUP BY m.chatRoom.id
        """)
    List<Object[]> findUnreadCounts(List<Long> roomIds, Long membershipId);

    /**
     * DM 방의 상대방 이름 일괄 조회
     * Object[]: [roomId, partnerName]
     */
    @Query("""
        SELECT cm.chatRoom.id, cm.member.name
        FROM ChatMember cm
        WHERE cm.chatRoom.id IN :roomIds
          AND cm.isLeft = false
          AND cm.member.id <> :membershipId
        """)
    List<Object[]> findDmPartnerNames(List<Long> roomIds, Long membershipId);
}
