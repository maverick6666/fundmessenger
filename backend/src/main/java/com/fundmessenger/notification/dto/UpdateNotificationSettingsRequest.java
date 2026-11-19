package com.fundmessenger.notification.dto;

import com.fundmessenger.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class UpdateNotificationSettingsRequest {

    @NotNull
    private List<SettingItem> settings;

    @Getter @Setter
    public static class SettingItem {
        @NotNull
        private NotificationType type;
        private Long teamId;
        private boolean enabled;
    }
}
