package com.fundmessenger.notification.dto;

import com.fundmessenger.notification.entity.Notification;
import com.fundmessenger.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private String targetUrl;
    private String metadata;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .targetUrl(n.getTargetUrl())
                .metadata(n.getMetadata())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
