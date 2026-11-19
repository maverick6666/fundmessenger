package com.fundmessenger.notification.service;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.notification.dto.NotificationResponse;
import com.fundmessenger.notification.dto.UpdateNotificationSettingsRequest;
import com.fundmessenger.notification.entity.Notification;
import com.fundmessenger.notification.entity.NotificationSetting;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.repository.NotificationRepository;
import com.fundmessenger.notification.repository.NotificationSettingRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationSettingRepository settingRepository;
    @Mock ClubRepository clubRepository;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks NotificationService notificationService;

    private Club club;
    private User user;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        user = TestFixtures.user(10L, "user@test.dev");
    }

    // ── notify ──

    @Nested
    @DisplayName("notify(알림 발행)")
    class NotifyTests {

        @Test
        @DisplayName("알림을 저장하고 WebSocket으로 전송한다")
        void notify_savesAndSendsViaWebSocket() {
            // given
            given(clubRepository.getReferenceById(1L)).willReturn(club);
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> {
                        Notification n = inv.getArgument(0);
                        n.setId(100L);
                        n.setCreatedAt(LocalDateTime.now());
                        return n;
                    });

            // when
            notificationService.notify(1L, 10L, NotificationType.NEW_NOTICE,
                    "새 공지", "공지 내용입니다", "/club");

            // then
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getType()).isEqualTo(NotificationType.NEW_NOTICE);
            assertThat(saved.getTitle()).isEqualTo("새 공지");
            assertThat(saved.getUser().getId()).isEqualTo(10L);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("10"), eq("/queue/notifications"), any(NotificationResponse.class));
        }

        @Test
        @DisplayName("비필수 알림이 OFF면 저장하지 않고 스킵한다")
        void notify_skipsWhenSettingDisabled() {
            // given — POST_COMMENT는 비필수
            NotificationSetting setting = NotificationSetting.builder()
                    .enabled(false).build();
            given(settingRepository.findByClubIdAndUserIdAndTypeAndTeamId(
                    1L, 10L, NotificationType.POST_COMMENT, null))
                    .willReturn(Optional.of(setting));

            // when
            notificationService.notify(1L, 10L, NotificationType.POST_COMMENT,
                    "댓글 알림", "내용", "/post/1");

            // then
            verify(notificationRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("필수 알림(TEAM_CHANGE)은 설정과 무관하게 저장한다")
        void notify_requiredTypeAlwaysSaves() {
            // given — TEAM_CHANGE는 필수
            given(clubRepository.getReferenceById(1L)).willReturn(club);
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> {
                        Notification n = inv.getArgument(0);
                        n.setId(101L);
                        n.setCreatedAt(LocalDateTime.now());
                        return n;
                    });

            // when
            notificationService.notify(1L, 10L, NotificationType.TEAM_CHANGE,
                    "팀 변경", "A팀으로 배정", "/club");

            // then — settingRepository 호출 없이 바로 저장
            verify(settingRepository, never()).findByClubIdAndUserIdAndTypeAndTeamId(
                    anyLong(), anyLong(), any(), any());
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    // ── getNotifications ──

    @Test
    @DisplayName("getNotifications — 사용자의 알림 목록을 페이지로 반환한다")
    void getNotifications_returnsPagedNotifications() {
        // given
        Notification n1 = Notification.builder()
                .club(club).user(user).type(NotificationType.NEW_NOTICE)
                .title("공지1").content("내용1").build();
        n1.setId(1L);
        n1.setCreatedAt(LocalDateTime.now());

        Page<Notification> page = new PageImpl<>(List.of(n1), PageRequest.of(0, 10), 1);
        given(notificationRepository.findByClubIdAndUserIdAndTypeNotOrderByIsReadAscCreatedAtDesc(
                eq(1L), eq(10L), any(), any(PageRequest.class))).willReturn(page);

        // when
        Page<NotificationResponse> result = notificationService.getNotifications(1L, 10L, 0, 10);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("공지1");
    }

    // ── markAsRead ──

    @Nested
    @DisplayName("markAsRead(읽음 처리)")
    class MarkAsReadTests {

        @Test
        @DisplayName("본인 알림의 isRead를 true로 설정한다")
        void markAsRead_setsReadTrue() {
            // given
            Notification n = Notification.builder()
                    .club(club).user(user).type(NotificationType.NEW_NOTICE)
                    .title("공지").content("내용").build();
            n.setId(50L);
            given(notificationRepository.findById(50L)).willReturn(Optional.of(n));

            // when
            notificationService.markAsRead(1L, 50L, 10L);

            // then
            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("타인의 알림을 읽음 처리하면 예외가 발생한다")
        void markAsRead_throwsForOtherUser() {
            // given
            Notification n = Notification.builder()
                    .club(club).user(user).type(NotificationType.NEW_NOTICE)
                    .title("공지").content("내용").build();
            n.setId(50L);
            given(notificationRepository.findById(50L)).willReturn(Optional.of(n));

            // when & then — userId 999는 소유자가 아님
            assertThatThrownBy(() -> notificationService.markAsRead(1L, 50L, 999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 알림만");
        }
    }

    // ── updateSettings ──

    @Test
    @DisplayName("updateSettings — 비필수 알림 설정을 OFF로 변경하면 새 행을 생성한다")
    void updateSettings_createsNewSettingForOff() {
        // given
        given(clubRepository.getReferenceById(1L)).willReturn(club);
        given(settingRepository.findByClubIdAndUserIdAndTypeAndTeamId(
                eq(1L), eq(10L), eq(NotificationType.POST_COMMENT), isNull()))
                .willReturn(Optional.empty());

        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
        UpdateNotificationSettingsRequest.SettingItem item = new UpdateNotificationSettingsRequest.SettingItem();
        item.setType(NotificationType.POST_COMMENT);
        item.setEnabled(false);
        item.setTeamId(null);
        request.setSettings(List.of(item));

        // when
        notificationService.updateSettings(1L, 10L, request);

        // then
        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(settingRepository).save(captor.capture());
        NotificationSetting saved = captor.getValue();
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getType()).isEqualTo(NotificationType.POST_COMMENT);
    }

    @Test
    @DisplayName("updateSettings — 필수 알림(TEAM_CHANGE)의 OFF 요청은 무시한다")
    void updateSettings_ignoresRequiredType() {
        // given
        given(clubRepository.getReferenceById(1L)).willReturn(club);

        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
        UpdateNotificationSettingsRequest.SettingItem item = new UpdateNotificationSettingsRequest.SettingItem();
        item.setType(NotificationType.TEAM_CHANGE); // 필수
        item.setEnabled(false);
        request.setSettings(List.of(item));

        // when
        notificationService.updateSettings(1L, 10L, request);

        // then — 필수 알림이므로 저장/조회 시도 없음
        verify(settingRepository, never()).save(any());
        verify(settingRepository, never()).findByClubIdAndUserIdAndTypeAndTeamId(
                anyLong(), anyLong(), any(), any());
    }
}
