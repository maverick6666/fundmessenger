package com.fundmessenger.chat.service;

import com.fundmessenger.chat.dto.ChatMessageResponse;
import com.fundmessenger.chat.dto.SendMessageRequest;
import com.fundmessenger.chat.entity.ChatMessage;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.entity.JoinMode;
import com.fundmessenger.chat.event.ChatMessageSentEvent;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import com.fundmessenger.chat.repository.ChatMessageRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageService 단위 테스트")
class ChatMessageServiceTest {

    @Mock ChatMessageRepository messageRepository;
    @Mock ChatMemberRepository memberRepository;
    @Mock ChatRoomRepository roomRepository;
    @Mock ClubMembershipRepository clubMembershipRepository;
    @Mock ClubRepository clubRepository;
    @Mock ClubAuthorizationService authService;
    @Mock ChatMemberService chatMemberService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks ChatMessageService chatMessageService;

    private Club club;
    private User user;
    private ClubMembership membership;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        user = TestFixtures.user(10L, "user@test.dev");
        membership = TestFixtures.approvedMembership(100L, user, club);

        room = ChatRoom.builder()
                .club(club).type(ChatRoomType.GROUP).name("스터디방")
                .joinMode(JoinMode.FREE).build();
        room.setId(50L);
    }

    @Test
    @DisplayName("sendMessage — 메시지를 저장하고 ChatMessageSentEvent를 발행한다")
    void sendMessage_savesAndPublishesEvent() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(membership);
        given(roomRepository.findById(50L)).willReturn(Optional.of(room));

        given(messageRepository.save(any(ChatMessage.class))).willAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            msg.setId(1000L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("안녕하세요!");

        // when
        ChatMessageResponse response = chatMessageService.sendMessage(1L, 50L, request, 10L);

        // then
        assertThat(response.getContent()).isEqualTo("안녕하세요!");
        assertThat(response.getSenderId()).isEqualTo(100L);

        ArgumentCaptor<ChatMessageSentEvent> captor = ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getMessages — beforeId가 null이면 최신 메시지를 반환한다")
    void getMessages_latestWhenNoCursor() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(membership);

        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room).sender(membership).content("최근 메시지").build();
        msg.setId(500L);
        msg.setCreatedAt(LocalDateTime.now());

        given(messageRepository.findLatest(eq(50L), any(PageRequest.class)))
                .willReturn(List.of(msg));

        // when
        List<ChatMessageResponse> result = chatMessageService.getMessages(1L, 50L, null, 20, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("최근 메시지");
    }

    @Test
    @DisplayName("getMessages — beforeId가 있으면 cursor 기반 조회한다")
    void getMessages_cursorBased() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(membership);

        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room).sender(membership).content("이전 메시지").build();
        msg.setId(400L);
        msg.setCreatedAt(LocalDateTime.now());

        given(messageRepository.findBeforeCursor(eq(50L), eq(500L), any(PageRequest.class)))
                .willReturn(List.of(msg));

        // when
        List<ChatMessageResponse> result = chatMessageService.getMessages(1L, 50L, 500L, 20, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(400L);
    }
}
