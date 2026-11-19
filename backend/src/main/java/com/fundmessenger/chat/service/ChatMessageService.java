package com.fundmessenger.chat.service;

import com.fundmessenger.chat.dto.*;
import com.fundmessenger.chat.entity.*;
import com.fundmessenger.chat.event.ChatMessageSentEvent;
import com.fundmessenger.chat.repository.*;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.jongtobang.entity.StockNewsCoupling;
import com.fundmessenger.jongtobang.repository.StockNewsCouplingRepository;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 메시지 전송 + 조회 + 콘텐츠 공유 (#1 God Class 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepository;
    private final ChatMemberRepository memberRepository;
    private final ChatRoomRepository roomRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final ChatMemberService chatMemberService;
    private final PostRepository postRepository;
    private final StockNewsCouplingRepository stockNewsCouplingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 메시지 조회 — cursor 기반 (#9)
     * beforeId가 null이면 최신, 있으면 해당 ID 이전 메시지
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long clubId, Long roomId, Long beforeId, int size, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        chatMemberService.validateRoomMember(roomId, membership.getId());

        List<ChatMessage> messages;
        if (beforeId == null) {
            messages = messageRepository.findLatest(roomId, PageRequest.of(0, size));
        } else {
            messages = messageRepository.findBeforeCursor(roomId, beforeId, PageRequest.of(0, size));
        }

        // DB에서는 DESC로 가져오므로, 클라이언트에서 시간순 정렬이 필요하면 reverse
        return messages.stream().map(this::toResponse).toList();
    }

    /**
     * 메시지 전송 — DB 저장 후 이벤트 발행 (#3 트랜잭션 경계)
     * WebSocket broadcast는 @TransactionalEventListener(AFTER_COMMIT)에서 처리
     */
    @Transactional
    public ChatMessageResponse sendMessage(Long clubId, Long roomId, SendMessageRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다"));
        chatMemberService.validateRoomMember(roomId, membership.getId());

        // content, imageUrl, sharedPostId, sharedNewsId 중 최소 하나는 있어야 함
        if ((request.getContent() == null || request.getContent().isBlank())
                && request.getImageUrl() == null
                && request.getSharedPostId() == null
                && request.getSharedNewsId() == null) {
            throw new IllegalArgumentException("메시지 내용, 이미지, 공유 콘텐츠 중 하나는 필수입니다");
        }

        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .sender(membership)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .sharedPostId(request.getSharedPostId())
                .sharedNewsId(request.getSharedNewsId())
                .build();

        ChatMessage saved = messageRepository.save(message);
        ChatMessageResponse response = toResponse(saved);

        // #3: 커밋 후 WebSocket 발송 (이벤트 → AFTER_COMMIT 리스너)
        eventPublisher.publishEvent(new ChatMessageSentEvent(roomId, response));

        // 채팅 메시지 알림 — 음소거 안 한 멤버 전원에게 발송
        List<Long> notifyUserIds = memberRepository.findByChatRoomIdAndIsLeftFalse(roomId).stream()
                .filter(cm -> !cm.getMember().getId().equals(membership.getId()))
                .filter(cm -> !cm.isMuted())
                .map(cm -> cm.getMember().getUser().getId())
                .toList();
        if (!notifyUserIds.isEmpty()) {
            String title = room.getType() == ChatRoomType.DM
                    ? membership.getName() + "님이 메시지를 보냈습니다"
                    : room.getName() + " — " + membership.getName();
            String preview = request.getContent() != null && request.getContent().length() > 50
                    ? request.getContent().substring(0, 50) + "…" : request.getContent();
            eventPublisher.publishEvent(NotificationEvent.ofMany(
                    clubId, notifyUserIds, NotificationType.DM_RECEIVED,
                    title, preview, "/club/chat/" + roomId
            ));
        }

        return response;
    }

    /** 콘텐츠 공유 */
    @Transactional
    public void shareContent(Long clubId, ContentShareRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Club club = clubRepository.findById(clubId).orElseThrow();

        // 기존 방에 공유
        if (request.getTargetRoomIds() != null) {
            for (Long targetRoomId : request.getTargetRoomIds()) {
                sendSharedMessage(targetRoomId, membership, request);
            }
        }

        // 멤버에게 DM으로 공유
        if (request.getTargetMemberIds() != null) {
            for (Long targetId : request.getTargetMemberIds()) {
                ChatRoom dmRoom = roomRepository.findDmRoom(clubId, membership.getId(), targetId)
                        .orElseGet(() -> {
                            ChatRoom newRoom = roomRepository.save(ChatRoom.builder()
                                    .club(club).type(ChatRoomType.DM).createdBy(membership).build());
                            chatMemberService.addMemberToRoom(newRoom, membership);
                            ClubMembership target = clubMembershipRepository.findById(targetId).orElseThrow();
                            chatMemberService.addMemberToRoom(newRoom, target);
                            return newRoom;
                        });
                sendSharedMessage(dmRoom.getId(), membership, request);
            }
        }
    }

    /**
     * 시스템 메시지 전송 — 토론방 생성, 세션 경계 등에서 사용
     * sender는 트리거한 사용자 (시스템 봇이 아님)
     */
    @Transactional
    public ChatMessage sendSystemMessage(ChatRoom room, ClubMembership sender, String content) {
        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(content)
                .build();
        ChatMessage saved = messageRepository.save(message);
        eventPublisher.publishEvent(new ChatMessageSentEvent(room.getId(), toResponse(saved)));
        return saved;
    }

    private void sendSharedMessage(Long roomId, ClubMembership sender, ContentShareRequest request) {
        ChatRoom room = roomRepository.findById(roomId).orElseThrow();
        ChatMessage message = ChatMessage.builder()
                .chatRoom(room).sender(sender)
                .content("[콘텐츠 공유]")
                .sharedPostId(request.getSharedPostId())
                .sharedNewsId(request.getSharedNewsId())
                .build();
        ChatMessage saved = messageRepository.save(message);
        // #3: 커밋 후 발송
        eventPublisher.publishEvent(new ChatMessageSentEvent(roomId, toResponse(saved)));
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        if (message.getSharedPostId() != null) {
            return postRepository.findById(message.getSharedPostId())
                    .map(post -> buildPostSharedResponse(message, post))
                    .orElseGet(() -> ChatMessageResponse.from(
                            message,
                            "POST",
                            "삭제되었거나 접근할 수 없는 게시글",
                            null
                    ));
        }

        if (message.getSharedNewsId() != null) {
            return stockNewsCouplingRepository.findById(message.getSharedNewsId())
                    .map(coupling -> buildNewsSharedResponse(message, coupling))
                    .orElseGet(() -> ChatMessageResponse.from(
                            message,
                            "NEWS",
                            "삭제되었거나 접근할 수 없는 뉴스",
                            null
                    ));
        }

        return ChatMessageResponse.from(message);
    }

    private ChatMessageResponse buildPostSharedResponse(ChatMessage message, Post post) {
        return ChatMessageResponse.from(
                message,
                "POST",
                post.getTitle(),
                "/club/boards?post=" + post.getId()
        );
    }

    private ChatMessageResponse buildNewsSharedResponse(ChatMessage message, StockNewsCoupling coupling) {
        return ChatMessageResponse.from(
                message,
                "NEWS",
                coupling.getCanonicalNews().getTitle(),
                "/club/jongtobang?stockId=" + coupling.getStock().getId() + "&newsId=" + coupling.getId()
        );
    }
}
