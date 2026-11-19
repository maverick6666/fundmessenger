package com.fundmessenger.notification.controller;

import com.fundmessenger.notification.entity.PushSubscription;
import com.fundmessenger.notification.repository.PushSubscriptionRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ClubRepository clubRepository;

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    /** VAPID 공개키 반환 (인증 불필요) */
    @GetMapping("/api/push/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", vapidPublicKey));
    }

    /** 푸시 구독 등록 */
    @PostMapping("/api/clubs/{clubId}/push/subscribe")
    @Transactional
    public ResponseEntity<Void> subscribe(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId,
            @RequestBody SubscribeRequest request) {

        // 기존 구독이 있으면 업데이트 (endpoint 기준)
        var existing = pushSubscriptionRepository.findByEndpoint(request.endpoint());
        if (existing.isPresent()) {
            PushSubscription sub = existing.get();
            sub.setP256dh(request.p256dh());
            sub.setAuthKey(request.auth());
            sub.setUserAgent(request.userAgent());
            return ResponseEntity.ok().build();
        }

        Club club = clubRepository.getReferenceById(clubId);
        User user = new User();
        user.setId(userId);

        pushSubscriptionRepository.save(PushSubscription.builder()
                .user(user)
                .club(club)
                .endpoint(request.endpoint())
                .p256dh(request.p256dh())
                .authKey(request.auth())
                .userAgent(request.userAgent())
                .build());

        return ResponseEntity.ok().build();
    }

    /** 푸시 구독 해제 */
    @DeleteMapping("/api/clubs/{clubId}/push/unsubscribe")
    @Transactional
    public ResponseEntity<Void> unsubscribe(
            @PathVariable Long clubId,
            @AuthenticationPrincipal Long userId,
            @RequestBody UnsubscribeRequest request) {

        pushSubscriptionRepository.deleteByEndpoint(request.endpoint());
        return ResponseEntity.ok().build();
    }

    record SubscribeRequest(String endpoint, String p256dh, String auth, String userAgent) {}
    record UnsubscribeRequest(String endpoint) {}
}
