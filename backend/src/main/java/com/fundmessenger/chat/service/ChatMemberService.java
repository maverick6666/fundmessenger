package com.fundmessenger.chat.service;

import com.fundmessenger.chat.dto.ChatMemberResponse;
import com.fundmessenger.chat.entity.ChatMember;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅방 멤버 관리 — 입장/나가기/읽음 (#1 God Class 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemberService {

    private final ChatMemberRepository memberRepository;
    private final ChatRoomRepository roomRepository;
    private final ClubAuthorizationService authService;
    private final NotificationService notificationService;

    /** 번개 입장 */
    @Transactional
    public void joinLightning(Long clubId, Long roomId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다"));

        if (room.getType() != ChatRoomType.LIGHTNING) {
            throw new IllegalArgumentException("번개 채팅방만 입장할 수 있습니다");
        }

        if (memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(roomId, membership.getId())) {
            return; // 이미 참여 중
        }

        if (room.getMaxMembers() != null) {
            long current = memberRepository.countByChatRoomIdAndIsLeftFalse(roomId);
            if (current >= room.getMaxMembers()) {
                throw new IllegalStateException("인원이 마감되었습니다");
            }
        }

        addMemberToRoom(room, membership);
        log.info("번개 입장: roomId={}, memberName={}", roomId, membership.getName());
    }

    /** 읽음 처리 */
    @Transactional
    public void markRead(Long clubId, Long roomId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        memberRepository.findByChatRoomIdAndMemberId(roomId, membership.getId())
                .ifPresent(cm -> {
                    cm.setLastReadAt(LocalDateTime.now());
                    notificationService.markChatRoomNotificationsAsRead(clubId, userId, roomId);
                });
    }

    /** 나가기 */
    @Transactional
    public void leaveRoom(Long clubId, Long roomId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다"));

        if (room.getType() == ChatRoomType.CLUB || room.getType() == ChatRoomType.TEAM) {
            throw new IllegalArgumentException("동아리/팀 채팅방은 나갈 수 없습니다");
        }

        memberRepository.findByChatRoomIdAndMemberId(roomId, membership.getId())
                .ifPresent(cm -> cm.setLeft(true));
    }

    /** 멤버 목록 */
    @Transactional(readOnly = true)
    public List<ChatMemberResponse> getRoomMembers(Long clubId, Long roomId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return memberRepository.findByChatRoomIdAndIsLeftFalse(roomId).stream()
                .map(ChatMemberResponse::from)
                .toList();
    }

    /** 알림 음소거 토글 */
    @Transactional
    public boolean toggleMute(Long clubId, Long roomId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        ChatMember cm = memberRepository.findByChatRoomIdAndMemberId(roomId, membership.getId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방 멤버가 아닙니다"));
        cm.setMuted(!cm.isMuted());
        return cm.isMuted();
    }

    /** 방에 멤버 추가 (중복 방지 + soft-leave 복구) */
    public void addMemberToRoom(ChatRoom room, ClubMembership membership) {
        if (memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(room.getId(), membership.getId())) {
            return;
        }
        var existing = memberRepository.findByChatRoomIdAndMemberId(room.getId(), membership.getId());
        if (existing.isPresent()) {
            existing.get().setLeft(false);
        } else {
            memberRepository.save(ChatMember.builder()
                    .chatRoom(room).member(membership).lastReadAt(LocalDateTime.now()).build());
        }
    }

    /** 방에서 멤버 제거 (soft-leave) — 팀 해제 등 시스템 용도 */
    public void removeMemberFromRoom(Long roomId, Long membershipId) {
        memberRepository.findByChatRoomIdAndMemberId(roomId, membershipId)
                .ifPresent(cm -> cm.setLeft(true));
    }

    /** 참여자 검증 */
    public void validateRoomMember(Long roomId, Long membershipId) {
        if (!memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(roomId, membershipId)) {
            throw new SecurityException("채팅방 멤버가 아닙니다");
        }
    }
}
