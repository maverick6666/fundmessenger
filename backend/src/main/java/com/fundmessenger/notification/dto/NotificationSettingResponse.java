package com.fundmessenger.notification.dto;

import com.fundmessenger.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSettingResponse {
    private NotificationType type;
    private String typeName;
    private boolean enabled;
    private boolean required;
    private Long teamId;
    private String teamName;

    private static final java.util.Map<NotificationType, String> TYPE_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry(NotificationType.TEAM_CHANGE, "팀 배정/해제"),
            java.util.Map.entry(NotificationType.ROLE_CHANGE, "임명/해제"),
            java.util.Map.entry(NotificationType.GENERATION_TRANSITION, "기수 전환"),
            java.util.Map.entry(NotificationType.POST_COMMENT, "내 글에 댓글"),
            java.util.Map.entry(NotificationType.COMMENT_REPLY, "내 댓글에 대댓글"),
            java.util.Map.entry(NotificationType.DRIVE_COMMENT, "내 파일에 코멘트"),
            java.util.Map.entry(NotificationType.CONTENT_LIKED, "좋아요"),
            java.util.Map.entry(NotificationType.TEAM_ACTIVITY, "팀 활동"),
            java.util.Map.entry(NotificationType.NEW_NOTICE, "공지"),
            java.util.Map.entry(NotificationType.AI_GENERATION, "AI 글 생성"),
            java.util.Map.entry(NotificationType.DM_RECEIVED, "DM 수신")
    );

    public static String getTypeName(NotificationType type) {
        return TYPE_NAMES.getOrDefault(type, type.name());
    }
}
