package com.fundmessenger.notification.service;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.notification.dto.NotificationResponse;
import com.fundmessenger.notification.dto.NotificationSettingResponse;
import com.fundmessenger.notification.dto.UpdateNotificationSettingsRequest;
import com.fundmessenger.notification.entity.Notification;
import com.fundmessenger.notification.entity.NotificationSetting;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.repository.NotificationRepository;
import com.fundmessenger.notification.repository.NotificationSettingRepository;
import com.fundmessenger.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final NotificationType PANEL_EXCLUDED_TYPE = NotificationType.DM_RECEIVED;

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final ClubRepository clubRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushService webPushService;

    // ── 알림 발행 (내부 호출) ──

    @Transactional
    public void notify(Long clubId, Long userId, NotificationType type,
                       String title, String content, String targetUrl, String metadata) {
        // 필수 알림이 아니면 설정 확인
        if (!type.isRequired()) {
            var setting = settingRepository.findByClubIdAndUserIdAndTypeAndTeamId(
                    clubId, userId, type, null);
            if (setting.isPresent() && !setting.get().isEnabled()) {
                return; // OFF 설정 → 스킵
            }
        }

        Club club = clubRepository.getReferenceById(clubId);
        User user = new User();
        user.setId(userId);

        Notification notification = Notification.builder()
                .club(club)
                .user(user)
                .type(type)
                .title(title)
                .content(content)
                .targetUrl(targetUrl)
                .metadata(metadata)
                .build();

        notificationRepository.save(notification);

        // WebSocket push
        try {
            NotificationResponse response = NotificationResponse.from(notification);
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), "/queue/notifications", response);
        } catch (Exception e) {
            log.warn("WebSocket push 실패 (userId={}): {}", userId, e.getMessage());
        }

        // Web Push (탭이 닫혀있을 때를 위해 항상 전송, SW에서 tag로 중복 억제)
        webPushService.sendPush(userId, clubId, title, content, targetUrl, type.name());
    }

    public void notify(Long clubId, Long userId, NotificationType type,
                       String title, String content, String targetUrl) {
        notify(clubId, userId, type, title, content, targetUrl, null);
    }

    // ── 팀 활동 알림 (팀별 설정 확인) ──

    @Transactional
    public void notifyTeamActivity(Long clubId, Long userId, Long teamId,
                                    String title, String content, String targetUrl) {
        var setting = settingRepository.findByClubIdAndUserIdAndTypeAndTeamId(
                clubId, userId, NotificationType.TEAM_ACTIVITY, teamId);
        if (setting.isPresent() && !setting.get().isEnabled()) {
            return;
        }
        notify(clubId, userId, NotificationType.TEAM_ACTIVITY, title, content, targetUrl);
    }

    // ── 조회 ──

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long clubId, Long userId, int page, int size) {
        return notificationRepository
                .findByClubIdAndUserIdAndTypeNotOrderByIsReadAscCreatedAtDesc(
                        clubId, userId, PANEL_EXCLUDED_TYPE, PageRequest.of(page, size))
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long clubId, Long userId) {
        return notificationRepository.countByClubIdAndUserIdAndIsReadFalseAndTypeNot(
                clubId, userId, PANEL_EXCLUDED_TYPE);
    }

    // ── 읽음 처리 ──

    @Transactional
    public void markAsRead(Long clubId, Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
        if (!n.getUser().getId().equals(userId) || !n.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("본인의 알림만 읽음 처리할 수 있습니다");
        }
        n.setRead(true);
    }

    @Transactional
    public void markAllAsRead(Long clubId, Long userId) {
        notificationRepository.markAllAsReadExcludingType(clubId, userId, PANEL_EXCLUDED_TYPE);
    }

    @Transactional
    public void markChatRoomNotificationsAsRead(Long clubId, Long userId, Long roomId) {
        notificationRepository.markAllAsReadByTypeAndTargetUrl(
                clubId, userId, NotificationType.DM_RECEIVED, "/club/chat/" + roomId);
    }

    // ── 설정 조회 ──

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> getSettings(Long clubId, Long userId) {
        Map<String, NotificationSetting> existing = settingRepository.findByClubIdAndUserId(clubId, userId)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getType() + ":" + s.getTeamId(),
                        s -> s
                ));

        List<NotificationSettingResponse> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            String key = type + ":null";
            NotificationSetting s = existing.get(key);
            boolean enabled = (s == null) || s.isEnabled(); // 행 없음 = ON

            result.add(NotificationSettingResponse.builder()
                    .type(type)
                    .typeName(NotificationSettingResponse.getTypeName(type))
                    .enabled(enabled)
                    .required(type.isRequired())
                    .build());
        }
        return result;
    }

    // ── 설정 변경 ──

    @Transactional
    public void updateSettings(Long clubId, Long userId, UpdateNotificationSettingsRequest request) {
        Club club = clubRepository.getReferenceById(clubId);
        User user = new User();
        user.setId(userId);

        for (var item : request.getSettings()) {
            if (item.getType().isRequired()) {
                continue; // 필수 알림 OFF 시도 무시
            }

            var existing = settingRepository.findByClubIdAndUserIdAndTypeAndTeamId(
                    clubId, userId, item.getType(), item.getTeamId());

            if (existing.isPresent()) {
                existing.get().setEnabled(item.isEnabled());
            } else if (!item.isEnabled()) {
                // OFF로 바꿀 때만 행 생성 (기본 ON)
                settingRepository.save(NotificationSetting.builder()
                        .club(club)
                        .user(user)
                        .type(item.getType())
                        .teamId(item.getTeamId())
                        .enabled(false)
                        .build());
            }
        }
    }
}
