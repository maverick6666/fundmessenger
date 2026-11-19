package com.fundmessenger.ai.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fundmessenger.ai.service.AiCommentBotService.ContentType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCommentBotScheduler {

    private final AiCommentBotService commentBotService;
    private ScheduledExecutorService executor;
    private final ReentrantLock dailyScheduleLock = new ReentrantLock();
    private final ReentrantLock weeklyReviewLock = new ReentrantLock();

    private static final int START_HOUR = 6;   // 06:00
    private static final int END_HOUR = 24;    // 24:00 (자정)
    private static final int WINDOW_MINUTES = (END_HOUR - START_HOUR) * 60; // 1080분

    @PostConstruct
    public void init() {
        executor = Executors.newScheduledThreadPool(2);
        // 서버 시작 후 30초 지연 — DB 스키마 업데이트 완료 후 스케줄 생성
        executor.schedule(() -> {
            LocalTime now = LocalTime.now();
            if (now.isAfter(LocalTime.of(START_HOUR, 0)) && now.isBefore(LocalTime.of(23, 59))) {
                log.info("AI 댓글봇 스케줄러: 서버 시작 시 당일 남은 일정 생성");
                try {
                    planDailyScheduleInternal(true);
                } catch (Exception e) {
                    log.warn("AI 댓글봇 초기 스케줄 생성 실패 (무시): {}", e.getMessage());
                }
            }
        }, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 매일 06:00에 하루 스케줄 생성
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void planDailySchedule() {
        if (!dailyScheduleLock.tryLock()) {
            log.warn("이전 AI 댓글봇 일정 생성 진행 중, 스킵");
            return;
        }
        try {
            planDailyScheduleInternal(false);
        } finally {
            dailyScheduleLock.unlock();
        }
    }

    /**
     * CB-7: 매주 토요일 00:00에 재조명글 발행 (금→토 넘어가는 자정)
     */
    @Scheduled(cron = "0 0 0 * * SAT")
    public void weeklyReview() {
        if (!weeklyReviewLock.tryLock()) {
            log.warn("이전 AI 재조명글 진행 중, 스킵");
            return;
        }
        try {
            List<Long> enabledClubs = commentBotService.getEnabledClubIds();
            if (enabledClubs.isEmpty()) {
                log.info("AI 재조명글: 활성화된 동아리 없음");
                return;
            }

            for (Long clubId : enabledClubs) {
                try {
                    var result = commentBotService.executeWeeklyReview(clubId);
                    log.info("AI 재조명글 실행 완료 (clubId={}): {}", clubId, result);
                } catch (Exception e) {
                    log.error("AI 재조명글 실행 실패 (clubId={}): {}", clubId, e.getMessage(), e);
                }
            }
        } finally {
            weeklyReviewLock.unlock();
        }
    }

    private void planDailyScheduleInternal(boolean midDay) {
        List<Long> enabledClubs = commentBotService.getEnabledClubIds();
        if (enabledClubs.isEmpty()) {
            log.info("AI 댓글봇: 활성화된 동아리 없음");
            return;
        }

        for (Long clubId : enabledClubs) {
            scheduleForClub(clubId, midDay);
        }
    }

    private void scheduleForClub(Long clubId, boolean midDay) {
        // CB-4: GlobalSettings에서 min/max 읽어서 캡 연동
        int[] capRange = commentBotService.getCommentBotCapRange();
        int capMin = capRange[0];
        int capMax = capRange[1];
        int count = capMin == capMax ? capMin : ThreadLocalRandom.current().nextInt(capMin, capMax + 1);

        // 4종류 각 최소 1개 보장 + 나머지 랜덤 (count < 4이면 보장분도 랜덤에서 선택)
        List<ContentType> actionTypes = new ArrayList<>();
        if (count >= 4) {
            List<ContentType> guaranteed = new ArrayList<>(Arrays.asList(ContentType.values()));
            Collections.shuffle(guaranteed);
            actionTypes.addAll(guaranteed); // 처음 4개: 각 타입 1개씩
            for (int i = 4; i < count; i++) {
                actionTypes.add(null); // 나머지: 랜덤 (null = service가 결정)
            }
        } else {
            // count < 4: 랜덤 타입만
            for (int i = 0; i < count; i++) {
                actionTypes.add(null);
            }
        }

        // N개 랜덤 분 오프셋 생성 (06:00 기준)
        List<Integer> offsets = new ArrayList<>();
        int availableMinutes = WINDOW_MINUTES;
        int startOffset = 0;

        if (midDay) {
            LocalTime now = LocalTime.now();
            int minutesSinceStart = (int) ChronoUnit.MINUTES.between(LocalTime.of(START_HOUR, 0), now);
            startOffset = Math.max(minutesSinceStart + 5, 0);
            availableMinutes = WINDOW_MINUTES - startOffset;
            if (availableMinutes <= 0) return;
        }

        for (int i = 0; i < count; i++) {
            offsets.add(startOffset + ThreadLocalRandom.current().nextInt(availableMinutes));
        }
        Collections.sort(offsets);

        // actionTypes도 offsets 순서에 맞게 재배열 (보장 타입이 하루 전반에 분산)
        List<ContentType> sortedActions = new ArrayList<>(actionTypes);
        Collections.shuffle(sortedActions);

        LocalDateTime today6am = LocalDateTime.now()
                .withHour(START_HOUR).withMinute(0).withSecond(0).withNano(0);

        for (int i = 0; i < offsets.size(); i++) {
            int offset = offsets.get(i);
            ContentType forcedType = sortedActions.get(i);
            LocalDateTime scheduledTime = today6am.plusMinutes(offset);
            long delayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), scheduledTime);
            if (delayMs < 0) continue;

            executor.schedule(() -> {
                try {
                    var result = commentBotService.executeRound(clubId, null, forcedType);
                    log.info("AI 댓글봇 실행 완료 (clubId={}, type={}): {}", clubId,
                            forcedType != null ? forcedType.name() : "RANDOM", result);
                } catch (Exception e) {
                    log.error("AI 댓글봇 실행 실패 (clubId={}, type={}): {}", clubId,
                            forcedType != null ? forcedType.name() : "RANDOM", e.getMessage(), e);
                }
            }, delayMs, TimeUnit.MILLISECONDS);

            log.debug("AI 댓글봇 예약: clubId={}, 시각={}, type={}", clubId, scheduledTime,
                    forcedType != null ? forcedType.name() : "RANDOM");
        }

        log.info("AI 댓글봇 하루 일정: clubId={}, {}회 ({})", clubId, offsets.size(),
                offsets.stream().map(o -> {
                    int h = START_HOUR + o / 60;
                    int m = o % 60;
                    return String.format("%02d:%02d", h, m);
                }).toList());
    }
}
