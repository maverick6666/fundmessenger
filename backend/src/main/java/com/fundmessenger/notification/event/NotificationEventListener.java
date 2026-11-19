package com.fundmessenger.notification.event;

import com.fundmessenger.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        for (Long userId : event.getTargetUserIds()) {
            try {
                if (event.getTeamId() != null) {
                    // 팀 활동 알림: 팀별 ON/OFF 설정 확인
                    notificationService.notifyTeamActivity(
                            event.getClubId(),
                            userId,
                            event.getTeamId(),
                            event.getTitle(),
                            event.getContent(),
                            event.getTargetUrl()
                    );
                } else {
                    notificationService.notify(
                            event.getClubId(),
                            userId,
                            event.getType(),
                            event.getTitle(),
                            event.getContent(),
                            event.getTargetUrl(),
                            event.getMetadata()
                    );
                }
            } catch (Exception e) {
                log.error("알림 발행 실패 (userId={}, type={}): {}",
                        userId, event.getType(), e.getMessage());
            }
        }
    }
}
