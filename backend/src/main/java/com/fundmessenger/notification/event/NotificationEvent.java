package com.fundmessenger.notification.event;

import com.fundmessenger.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class NotificationEvent {
    private final Long clubId;
    private final List<Long> targetUserIds;
    private final NotificationType type;
    private final String title;
    private final String content;
    private final String targetUrl;
    private final String metadata;
    /** TEAM_ACTIVITY 전용: 팀별 ON/OFF 설정 확인용 */
    private final Long teamId;

    public static NotificationEvent of(Long clubId, Long targetUserId,
                                        NotificationType type, String title,
                                        String content, String targetUrl) {
        return NotificationEvent.builder()
                .clubId(clubId)
                .targetUserIds(List.of(targetUserId))
                .type(type)
                .title(title)
                .content(content)
                .targetUrl(targetUrl)
                .build();
    }

    public static NotificationEvent ofMany(Long clubId, List<Long> targetUserIds,
                                            NotificationType type, String title,
                                            String content, String targetUrl) {
        return NotificationEvent.builder()
                .clubId(clubId)
                .targetUserIds(targetUserIds)
                .type(type)
                .title(title)
                .content(content)
                .targetUrl(targetUrl)
                .build();
    }

    /** 팀 활동 알림 (팀별 설정 확인 포함) */
    public static NotificationEvent ofTeamActivity(Long clubId, List<Long> targetUserIds,
                                                    Long teamId, String title,
                                                    String content, String targetUrl) {
        return NotificationEvent.builder()
                .clubId(clubId)
                .targetUserIds(targetUserIds)
                .type(NotificationType.TEAM_ACTIVITY)
                .teamId(teamId)
                .title(title)
                .content(content)
                .targetUrl(targetUrl)
                .build();
    }
}
