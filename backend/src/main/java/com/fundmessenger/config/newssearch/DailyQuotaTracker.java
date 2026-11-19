package com.fundmessenger.config.newssearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 키별 일일 사용량 추적. 자정에 자동 리셋.
 * 키 = "TAVILY:0", "SERPAPI:1" 등.
 */
@Slf4j
@Component
public class DailyQuotaTracker {

    private final ConcurrentHashMap<String, AtomicInteger> dailyUsage = new ConcurrentHashMap<>();
    private volatile LocalDate currentDate = LocalDate.now();

    /**
     * 사용량 1 소비 시도. 한도 초과 시 false 반환.
     */
    public boolean tryConsume(NewsSearchProvider provider, int keyIndex, int dailyLimit) {
        checkDateRollover();
        String key = provider.name() + ":" + keyIndex;
        AtomicInteger counter = dailyUsage.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.get();
        if (current >= dailyLimit) {
            return false;
        }
        counter.incrementAndGet();
        return true;
    }

    /** 특정 provider의 모든 키가 소진되었는지 */
    public boolean isProviderExhausted(NewsSearchProvider provider, int keyCount, int dailyLimit) {
        checkDateRollover();
        for (int i = 0; i < keyCount; i++) {
            String key = provider.name() + ":" + i;
            AtomicInteger counter = dailyUsage.get(key);
            if (counter == null || counter.get() < dailyLimit) {
                return false;
            }
        }
        return true;
    }

    /** 모니터링용 사용량 리포트 */
    public Map<String, Object> getUsageReport() {
        checkDateRollover();
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("date", currentDate.toString());
        dailyUsage.forEach((key, counter) ->
                report.put(key, counter.get()));
        return report;
    }

    private void checkDateRollover() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    dailyUsage.values().forEach(c -> c.set(0));
                    currentDate = today;
                    log.info("뉴스 검색 일일 할당량 리셋 ({})", today);
                }
            }
        }
    }
}
