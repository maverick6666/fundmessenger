package com.fundmessenger.chat.service;

import com.fundmessenger.chat.dto.*;
import com.fundmessenger.chat.entity.*;
import com.fundmessenger.chat.repository.*;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.TradeRequest;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 채팅방 CRUD + 자동생성 (#1 God Class 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatMemberRepository memberRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final ChatMemberService chatMemberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 내 채팅방 목록 — 3~4 batch 쿼리로 메타 조회 (5 scalar subqueries → JOIN+GROUP BY 리팩터)
     * 20방 기준: 100+ DB 히트 → 최대 4 히트
     */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyRooms(Long clubId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Long membershipId = membership.getId();

        // Q1: 방 목록 + 멤버 수 (JOIN + GROUP BY)
        List<Object[]> roomRows = roomRepository.findMyRoomsWithMemberCount(clubId, membershipId);
        if (roomRows.isEmpty()) return List.of();

        List<Long> roomIds = roomRows.stream()
                .map(row -> ((ChatRoom) row[0]).getId())
                .toList();

        // Q2: 마지막 메시지 일괄 조회
        Map<Long, Object[]> lastMsgMap = new HashMap<>();
        for (Object[] row : roomRepository.findLastMessages(roomIds)) {
            lastMsgMap.put((Long) row[0], row);
        }

        // Q3: 미읽음 수 일괄 조회
        Map<Long, Long> unreadMap = new HashMap<>();
        for (Object[] row : roomRepository.findUnreadCounts(roomIds, membershipId)) {
            unreadMap.put((Long) row[0], (Long) row[1]);
        }

        // Q4: DM 파트너 이름 일괄 조회 (DM 방이 있을 때만)
        List<Long> dmRoomIds = roomRows.stream()
                .filter(row -> ((ChatRoom) row[0]).getType() == ChatRoomType.DM)
                .map(row -> ((ChatRoom) row[0]).getId())
                .toList();
        Map<Long, String> dmNameMap = new HashMap<>();
        if (!dmRoomIds.isEmpty()) {
            for (Object[] row : roomRepository.findDmPartnerNames(dmRoomIds, membershipId)) {
                dmNameMap.put((Long) row[0], (String) row[1]);
            }
        }

        // Q5: muted 상태 일괄 조회
        Map<Long, Boolean> mutedMap = new HashMap<>();
        for (ChatMember cm : memberRepository.findByMemberIdAndChatRoomIdIn(membershipId, roomIds)) {
            mutedMap.put(cm.getChatRoom().getId(), cm.isMuted());
        }

        // 조합
        return roomRows.stream().map(row -> {
            ChatRoom room = (ChatRoom) row[0];
            long memberCount = (long) row[1];
            Long roomId = room.getId();

            Object[] lastMsg = lastMsgMap.get(roomId);
            String lastMessage = lastMsg != null ? (String) lastMsg[1] : null;
            java.time.LocalDateTime lastMessageAt = lastMsg != null ? (java.time.LocalDateTime) lastMsg[2] : null;
            long unreadCount = unreadMap.getOrDefault(roomId, 0L);

            // DM 방: 상대방 이름으로 표시
            String displayName = room.getName();
            if (room.getType() == ChatRoomType.DM && displayName == null) {
                String dmPartnerName = dmNameMap.get(roomId);
                displayName = dmPartnerName != null ? dmPartnerName : "DM";
            }

            return ChatRoomResponse.builder()
                    .id(roomId)
                    .type(room.getType().name())
                    .name(displayName)
                    .description(room.getDescription())
                    .maxMembers(room.getMaxMembers())
                    .joinMode(room.getJoinMode().name())
                    .teamId(room.getTeam() != null ? room.getTeam().getId() : null)
                    .teamName(room.getTeam() != null ? room.getTeam().getName() : null)
                    .memberCount(memberCount)
                    .unreadCount(unreadCount)
                    .lastMessage(lastMessage)
                    .lastMessageAt(lastMessageAt)
                    .createdAt(room.getCreatedAt())
                    .muted(mutedMap.getOrDefault(roomId, false))
                    .build();
        }).toList();
    }

    @Transactional
    public ChatRoomResponse createRoom(Long clubId, CreateChatRoomRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        ChatRoomType type = ChatRoomType.valueOf(request.getType());

        // DM: 기존 방 재활용
        if (type == ChatRoomType.DM && request.getMemberIds() != null && request.getMemberIds().size() == 1) {
            Long targetId = request.getMemberIds().get(0);
            var existing = roomRepository.findDmRoom(clubId, membership.getId(), targetId);
            if (existing.isPresent()) {
                ChatRoom room = existing.get();
                // 나가기한 경우 복구
                memberRepository.findByChatRoomIdAndMemberId(room.getId(), membership.getId())
                        .ifPresent(cm -> cm.setLeft(false));
                return buildSimpleResponse(room);
            }
        }

        ChatRoom room = ChatRoom.builder()
                .club(club)
                .type(type)
                .name(request.getName())
                .description(request.getDescription())
                .maxMembers(request.getMaxMembers())
                .joinMode(request.getJoinMode() != null ? JoinMode.valueOf(request.getJoinMode()) : JoinMode.FREE)
                .createdBy(membership)
                .build();
        room = roomRepository.save(room);

        // 생성자 자동 참여
        chatMemberService.addMemberToRoom(room, membership);

        // 멤버 추가
        if (request.getMemberIds() != null) {
            for (Long mid : request.getMemberIds()) {
                ClubMembership target = clubMembershipRepository.findById(mid).orElse(null);
                if (target != null && target.getClub().getId().equals(clubId)) {
                    chatMemberService.addMemberToRoom(room, target);
                }
            }
        }

        log.info("채팅방 생성: clubId={}, type={}, roomId={}", clubId, type, room.getId());

        // 오픈채팅방 생성 시 동아리 전원에게 알림
        if (type == ChatRoomType.LIGHTNING) {
            final ChatRoom savedRoom = room;
            List<Long> targetUserIds = clubMembershipRepository
                    .findByClubIdAndStatus(clubId, MemberStatus.APPROVED).stream()
                    .filter(m -> !m.getUser().getId().equals(userId))
                    .map(m -> m.getUser().getId())
                    .toList();
            if (!targetUserIds.isEmpty()) {
                eventPublisher.publishEvent(NotificationEvent.ofMany(
                        clubId, targetUserIds,
                        NotificationType.OPEN_CHAT_CREATED,
                        "⚡ " + savedRoom.getName(),
                        savedRoom.getDescription() != null ? savedRoom.getDescription() : membership.getName() + "님이 오픈채팅방을 만들었습니다",
                        "/club/chat/" + savedRoom.getId()
                ));
            }
        }

        return buildSimpleResponse(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getLightningRooms(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return roomRepository.findLightningRooms(clubId).stream()
                .map(this::buildSimpleResponse)
                .toList();
    }

    /** 동아리 채팅방 자동 생성 */
    @Transactional
    public ChatRoom ensureClubChat(Club club) {
        return roomRepository.findByClubIdAndType(club.getId(), ChatRoomType.CLUB)
                .orElseGet(() -> {
                    ChatRoom room = roomRepository.save(ChatRoom.builder()
                            .club(club).type(ChatRoomType.CLUB).name(club.getName() + " 채팅")
                            .build()); // createdBy nullable — 시스템 자동생성 (#4/#12)
                    clubMembershipRepository.findByClubIdAndStatus(club.getId(), MemberStatus.APPROVED)
                            .stream().filter(m -> !m.isBot())
                            .forEach(m -> chatMemberService.addMemberToRoom(room, m));
                    return room;
                });
    }

    /** 팀 채팅방 자동 생성 */
    @Transactional
    public ChatRoom ensureTeamChat(Club club, Team team) {
        return roomRepository.findByClubIdAndTeamIdAndType(club.getId(), team.getId(), ChatRoomType.TEAM)
                .orElseGet(() -> roomRepository.save(ChatRoom.builder()
                        .club(club).type(ChatRoomType.TEAM).team(team)
                        .name(team.getName() + " 채팅")
                        .build())); // createdBy nullable — 시스템 자동생성 (#4/#12)
    }

    /**
     * DISCUSSION 토론방 생성 — TradeRequest 토론 전환 시 호출
     * 같은 팀+종목의 기존 토론방이 있으면 재사용
     */
    @Transactional
    public ChatRoom createDiscussionRoom(Club club, Team team, TradeRequest tradeRequest,
                                          Position position, List<ClubMembership> members) {
        String roomName = tradeRequest.getTargetTickerName() + " 토론";

        // 같은 팀+종목의 기존 토론방 재사용
        Optional<ChatRoom> existing = roomRepository.findDiscussionRoom(
                club.getId(), team.getId(), roomName);
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            // 기존 방에 tradeRequest 연결 갱신
            room.setTradeRequest(tradeRequest);
            if (position != null) {
                room.setPosition(position);
            }
            // 새 멤버 추가 (기존 멤버는 addMemberToRoom에서 중복 방지)
            for (ClubMembership m : members) {
                chatMemberService.addMemberToRoom(room, m);
            }
            return roomRepository.save(room);
        }

        ChatRoom room = ChatRoom.builder()
                .club(club)
                .type(ChatRoomType.DISCUSSION)
                .team(team)
                .name(roomName)
                .description(tradeRequest.getRequestType().name() + " 요청 토론")
                .tradeRequest(tradeRequest)
                .position(position)
                .build();
        room = roomRepository.save(room);

        // 멤버 추가
        for (ClubMembership m : members) {
            chatMemberService.addMemberToRoom(room, m);
        }

        log.info("토론방 생성: clubId={}, teamId={}, ticker={}, roomId={}",
                club.getId(), team.getId(), tradeRequest.getTargetTicker(), room.getId());
        return room;
    }

    /** 팀 채팅방 조회 (없으면 empty) */
    @Transactional(readOnly = true)
    public Optional<ChatRoom> findTeamChat(Long clubId, Long teamId) {
        return roomRepository.findByClubIdAndTeamIdAndType(clubId, teamId, ChatRoomType.TEAM);
    }

    public ChatRoom getRoomOrThrow(Long roomId, Long clubId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + roomId));
        if (!room.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 채팅방이 아닙니다");
        }
        return room;
    }

    private ChatRoomResponse buildSimpleResponse(ChatRoom room) {
        long memberCount = memberRepository.countByChatRoomIdAndIsLeftFalse(room.getId());
        return ChatRoomResponse.from(room, memberCount, 0, null, null);
    }
}
