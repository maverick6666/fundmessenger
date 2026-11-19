package com.fundmessenger.chat.service;

import com.fundmessenger.chat.entity.ChatMember;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.entity.JoinMode;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMemberService 단위 테스트")
class ChatMemberServiceTest {

    @Mock ChatMemberRepository memberRepository;
    @Mock ChatRoomRepository roomRepository;
    @Mock ClubAuthorizationService authService;

    @InjectMocks ChatMemberService chatMemberService;

    private Club club;
    private User user;
    private ClubMembership membership;
    private ChatRoom lightningRoom;
    private ChatRoom groupRoom;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        user = TestFixtures.user(10L, "user@test.dev");
        membership = TestFixtures.approvedMembership(100L, user, club);

        lightningRoom = ChatRoom.builder()
                .club(club).type(ChatRoomType.LIGHTNING).name("번개 모임")
                .joinMode(JoinMode.FREE).maxMembers(10).build();
        lightningRoom.setId(50L);

        groupRoom = ChatRoom.builder()
                .club(club).type(ChatRoomType.GROUP).name("그룹 채팅")
                .joinMode(JoinMode.FREE).build();
        groupRoom.setId(60L);
    }

    // ── joinLightning ──

    @Nested
    @DisplayName("joinLightning(번개 입장)")
    class JoinLightningTests {

        @Test
        @DisplayName("번개 채팅방에 성공적으로 입장한다")
        void joinLightning_success() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(roomRepository.findById(50L)).willReturn(Optional.of(lightningRoom));
            given(memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(50L, 100L)).willReturn(false);
            given(memberRepository.countByChatRoomIdAndIsLeftFalse(50L)).willReturn(3L);
            // addMemberToRoom 내부 호출 mock
            given(memberRepository.findByChatRoomIdAndMemberId(50L, 100L)).willReturn(Optional.empty());

            // when
            chatMemberService.joinLightning(1L, 50L, 10L);

            // then
            verify(memberRepository).save(any(ChatMember.class));
        }

        @Test
        @DisplayName("번개가 아닌 방에 입장하면 예외가 발생한다")
        void joinLightning_rejectsNonLightning() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(roomRepository.findById(60L)).willReturn(Optional.of(groupRoom));

            // when & then
            assertThatThrownBy(() -> chatMemberService.joinLightning(1L, 60L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("번개 채팅방만");
        }

        @Test
        @DisplayName("인원이 마감되면 예외가 발생한다")
        void joinLightning_throwsWhenFull() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(roomRepository.findById(50L)).willReturn(Optional.of(lightningRoom));
            given(memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(50L, 100L)).willReturn(false);
            given(memberRepository.countByChatRoomIdAndIsLeftFalse(50L)).willReturn(10L); // maxMembers = 10

            // when & then
            assertThatThrownBy(() -> chatMemberService.joinLightning(1L, 50L, 10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("인원이 마감");
        }
    }

    // ── leaveRoom ──

    @Nested
    @DisplayName("leaveRoom(나가기)")
    class LeaveRoomTests {

        @Test
        @DisplayName("GROUP 방에서 나가면 isLeft=true로 설정된다")
        void leaveRoom_setsLeftTrue() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(roomRepository.findById(60L)).willReturn(Optional.of(groupRoom));

            ChatMember chatMember = ChatMember.builder()
                    .chatRoom(groupRoom).member(membership).build();
            chatMember.setId(1L);
            given(memberRepository.findByChatRoomIdAndMemberId(60L, 100L))
                    .willReturn(Optional.of(chatMember));

            // when
            chatMemberService.leaveRoom(1L, 60L, 10L);

            // then
            assertThat(chatMember.isLeft()).isTrue();
        }

        @Test
        @DisplayName("CLUB 방은 나갈 수 없다")
        void leaveRoom_rejectsClubRoom() {
            // given
            ChatRoom clubRoom = ChatRoom.builder()
                    .club(club).type(ChatRoomType.CLUB).name("동아리 채팅")
                    .joinMode(JoinMode.FREE).build();
            clubRoom.setId(70L);

            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(roomRepository.findById(70L)).willReturn(Optional.of(clubRoom));

            // when & then
            assertThatThrownBy(() -> chatMemberService.leaveRoom(1L, 70L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("동아리/팀 채팅방은 나갈 수 없습니다");
        }
    }

    // ── addMemberToRoom ──

    @Test
    @DisplayName("addMemberToRoom — 이미 참여 중이면 중복 추가하지 않는다")
    void addMemberToRoom_skipsDuplicate() {
        // given
        given(memberRepository.existsByChatRoomIdAndMemberIdAndIsLeftFalse(60L, 100L)).willReturn(true);

        // when
        chatMemberService.addMemberToRoom(groupRoom, membership);

        // then
        verify(memberRepository, never()).save(any());
    }
}
