package com.fundmessenger.notification.controller;

import com.fundmessenger.notification.dto.NotificationResponse;
import com.fundmessenger.notification.dto.NotificationSettingResponse;
import com.fundmessenger.notification.dto.UpdateNotificationSettingsRequest;
import com.fundmessenger.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getNotifications(clubId, userId, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId) {
        return Map.of("count", notificationService.getUnreadCount(clubId, userId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long clubId,
            @PathVariable Long notificationId,
            @AuthenticationPrincipal Long userId) {
        notificationService.markAsRead(clubId, notificationId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(clubId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings")
    public List<NotificationSettingResponse> getSettings(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId) {
        return notificationService.getSettings(clubId, userId);
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        notificationService.updateSettings(clubId, userId, request);
        return ResponseEntity.ok().build();
    }
}
