package com.fundmessenger.notification.repository;

import com.fundmessenger.notification.entity.NotificationSetting;
import com.fundmessenger.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByClubIdAndUserId(Long clubId, Long userId);

    Optional<NotificationSetting> findByClubIdAndUserIdAndTypeAndTeamId(
            Long clubId, Long userId, NotificationType type, Long teamId);
}
