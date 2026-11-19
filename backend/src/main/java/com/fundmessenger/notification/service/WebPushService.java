package com.fundmessenger.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.notification.entity.PushSubscription;
import com.fundmessenger.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${webpush.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${webpush.vapid.subject:mailto:fund@messenger.app}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()) {
            log.info("VAPID 키 미설정 — Web Push 비활성화");
            return;
        }

        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            log.info("Web Push 서비스 초기화 완료");
        } catch (Exception e) {
            log.error("Web Push 서비스 초기화 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 유저의 모든 디바이스에 Web Push 전송
     */
    @Async
    public void sendPush(Long userId, Long clubId, String title, String body,
                         String targetUrl, String type) {
        if (pushService == null) return;

        List<PushSubscription> subs = subscriptionRepository.findByUserIdAndClubId(userId, clubId);
        if (subs.isEmpty()) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", title != null ? title : "펀드메신저",
                    "body", body != null ? body : "",
                    "targetUrl", targetUrl != null ? targetUrl : "/club",
                    "type", type != null ? type : "GENERAL",
                    "tag", "fm-" + (type != null ? type.toLowerCase() : "general")
            ));
        } catch (Exception e) {
            log.warn("Push payload 직렬화 실패: {}", e.getMessage());
            return;
        }

        for (PushSubscription sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuthKey(),
                        payload.getBytes(StandardCharsets.UTF_8)
                );

                HttpResponse response = pushService.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 201) {
                    log.debug("Web Push 전송 성공 (userId={}, endpoint=...{})",
                            userId, sub.getEndpoint().substring(Math.max(0, sub.getEndpoint().length() - 20)));
                } else if (statusCode == 410 || statusCode == 404) {
                    // 만료된 구독 → 자동 삭제
                    log.info("만료된 Push 구독 삭제 (userId={}, status={})", userId, statusCode);
                    subscriptionRepository.delete(sub);
                } else {
                    log.warn("Web Push 전송 실패 (userId={}, status={})", userId, statusCode);
                }
            } catch (Exception e) {
                log.warn("Web Push 전송 예외 (userId={}): {}", userId, e.getMessage());
            }
        }
    }
}
