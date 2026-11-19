package com.fundmessenger.chat.service;

import com.fundmessenger.chat.dto.ChatRoomResponse;
import com.fundmessenger.chat.dto.CreateChatRoomRequest;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.entity.JoinMode;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatRoomService 단위 테스트")
class ChatRoomServiceTest {

    @Mock ChatRoomRepository roomRepository;
    @Mock ChatMemberRepository memberRepository;
    @Mock ClubMembershipRepository clubMembershipRepository;
    @Mock ClubRepository clubRepository;
    @Mock ClubAuthorizationService authService;
    @Mock ChatMemberService chatMemberService;

    @InjectMocks ChatRoomService chatRoomService;

    private Club club;
    private User user;
    private ClubMembership membership;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        user = TestFixtures.user(10L, "user@test.dev");
        membership = TestFixtures.approvedMembership(100L, user, club);
    }

    // ── createRoom ──

    @Nested
    @DisplayName("createRoom(채팅방 생성)")
    class CreateRoomTests {

        @Test
        @DisplayName("GROUP 채팅방을 생성하고 응답을 반환한다")
        void createRoom_groupSuccess() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(clubRepository.findById(1L)).willReturn(Optional.of(club));
            given(roomRepository.save(any(ChatRoom.class))).willAnswer(inv -> {
                ChatRoom room = inv.getArgument(0);
                room.setId(200L);
                return room;
            });
            given(memberRepository.countByChatRoomIdAndIsLeftFalse(200L)).willReturn(1L);

            CreateChatRoomRequest request = new CreateChatRoomRequest();
            request.setType("GROUP");
            request.setName("스터디 채팅");
            request.setDescription("함께 공부해요");

            // when
            ChatRoomResponse response = chatRoomService.createRoom(1L, request, 10L);

            // then
            assertThat(response.getId()).isEqualTo(200L);
            assertThat(response.getType()).isEqualTo("GROUP");
            assertThat(response.getName()).isEqualTo("스터디 채팅");
            verify(chatMemberService).addMemberToRoom(any(ChatRoom.class), eq(membership));
        }

        @Test
        @DisplayName("DM 방이 이미 존재하면 기존 방을 재활용한다")
        void createRoom_dmReuseExisting() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(membership);
            given(clubRepository.findById(1L)).willReturn(Optional.of(club));

            ChatRoom existingRoom = ChatRoom.builder()
                    .club(club).type(ChatRoomType.DM).joinMode(JoinMode.FREE).build();
            existingRoom.setId(300L);

            given(roomRepository.findDmRoom(1L, 100L, 200L)).willReturn(Optional.of(existingRoom));
            given(memberRepository.findByChatRoomIdAndMemberId(300L, 100L)).willReturn(Optional.empty());
            given(memberRepository.countByChatRoomIdAndIsLeftFalse(300L)).willReturn(2L);

            CreateChatRoomRequest request = new CreateChatRoomRequest();
            request.setType("DM");
            request.setMemberIds(List.of(200L));

            // when
            ChatRoomResponse response = chatRoomService.createRoom(1L, request, 10L);

            // then
            assertThat(response.getId()).isEqualTo(300L);
            assertThat(response.getType()).isEqualTo("DM");
        }
    }

    // ── getMyRooms ──

    @Test
    @DisplayName("getMyRooms — 채팅방이 없으면 빈 리스트를 반환한다")
    void getMyRooms_emptyWhenNoRooms() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(membership);
        given(roomRepository.findMyRoomsWithMemberCount(1L, 100L)).willReturn(List.of());

        // when
        List<ChatRoomResponse> result = chatRoomService.getMyRooms(1L, 10L);

        // then
        assertThat(result).isEmpty();
    }

    // ── ensureClubChat ──

    @Test
    @DisplayName("ensureClubChat — 기존 CLUB 방이 없으면 자동 생성한다")
    void ensureClubChat_createsWhenAbsent() {
        // given
        given(roomRepository.findByClubIdAndType(1L, ChatRoomType.CLUB)).willReturn(Optional.empty());
        given(roomRepository.save(any(ChatRoom.class))).willAnswer(inv -> {
            ChatRoom room = inv.getArgument(0);
            room.setId(500L);
            return room;
        });
        given(clubMembershipRepository.findByClubIdAndStatus(1L, MemberStatus.APPROVED))
                .willReturn(List.of(membership));

        // when
        ChatRoom result = chatRoomService.ensureClubChat(club);

        // then
        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getType()).isEqualTo(ChatRoomType.CLUB);
        verify(chatMemberService).addMemberToRoom(any(ChatRoom.class), eq(membership));
    }

    // ── getRoomOrThrow ──

    @Test
    @DisplayName("getRoomOrThrow — 다른 동아리의 채팅방이면 SecurityException 발생")
    void getRoomOrThrow_throwsForWrongClub() {
        // given
        Club otherClub = TestFixtures.club(2L, "다른동아리");
        ChatRoom room = ChatRoom.builder().club(otherClub).type(ChatRoomType.GROUP)
                .joinMode(JoinMode.FREE).build();
        room.setId(999L);
        given(roomRepository.findById(999L)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> chatRoomService.getRoomOrThrow(999L, 1L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("해당 동아리의 채팅방이 아닙니다");
    }
}
