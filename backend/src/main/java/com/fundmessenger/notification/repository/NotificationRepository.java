package com.fundmessenger.notification.repository;

import com.fundmessenger.notification.entity.Notification;
import com.fundmessenger.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByClubIdAndUserIdAndTypeNotOrderByIsReadAscCreatedAtDesc(
            Long clubId, Long userId, NotificationType excludedType, Pageable pageable);

    long countByClubIdAndUserIdAndIsReadFalseAndTypeNot(Long clubId, Long userId, NotificationType excludedType);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
           "WHERE n.club.id = :clubId AND n.user.id = :userId AND n.type <> :excludedType AND n.isRead = false")
    int markAllAsReadExcludingType(Long clubId, Long userId, NotificationType excludedType);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
           "WHERE n.club.id = :clubId AND n.user.id = :userId AND n.type = :type " +
           "AND n.targetUrl = :targetUrl AND n.isRead = false")
    int markAllAsReadByTypeAndTargetUrl(Long clubId, Long userId, NotificationType type, String targetUrl);

    boolean existsByClubIdAndTargetUrl(Long clubId, String targetUrl);
}
