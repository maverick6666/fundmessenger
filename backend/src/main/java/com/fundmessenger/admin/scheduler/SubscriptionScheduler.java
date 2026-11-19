package com.fundmessenger.admin.scheduler;

import com.fundmessenger.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final AdminService adminService;

    /**
     * 매일 자정에 만료된 구독을 EXPIRED 처리
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void checkExpiredSubscriptions() {
        int count = adminService.expireOverdueSubscriptions();
        if (count > 0) {
            log.info("구독 자동 만료 처리: {}건", count);
        }
    }
}
