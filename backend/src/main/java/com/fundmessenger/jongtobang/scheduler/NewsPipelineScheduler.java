package com.fundmessenger.jongtobang.scheduler;

import com.fundmessenger.jongtobang.service.NewsPipelineService;
import com.fundmessenger.jongtobang.service.SurgeReasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jongtobang.news-pipeline", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NewsPipelineScheduler {

    private final NewsPipelineService newsPipelineService;
    private final SurgeReasonService surgeReasonService;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("=== NewsPipelineScheduler 활성화됨 ===");
    }

    /**
     * 뉴스 파이프라인: 1시간마다 (서버 시작 2분 후 첫 실행)
     * 1) 일반 뉴스 수집+클러스터링+AI 생성
     * 2) 급등 종목 이유 검색
     */
    @Scheduled(
            fixedDelayString = "${jongtobang.news-pipeline.fixed-delay-ms:3600000}",
            initialDelayString = "${jongtobang.news-pipeline.initial-delay-ms:120000}")
    public void runScheduledPipeline() {
        log.info("=== 뉴스 파이프라인 스케줄 실행 시작 ===");
        long start = System.currentTimeMillis();

        // 1) 일반 뉴스 파이프라인
        boolean started = newsPipelineService.runPipeline();
        if (!started) {
            log.info("일반 뉴스 파이프라인 스킵 (이미 실행 중)");
        }

        // 2) 급등 종목 이유 검색
        try {
            int surgeCreated = surgeReasonService.runSurgeReasonPipeline();
            if (surgeCreated > 0) {
                log.info("급등 이유 검색으로 {}건 뉴스 생성", surgeCreated);
            }
        } catch (Exception e) {
            log.warn("급등 이유 검색 실패", e);
        }

        log.info("=== 뉴스 파이프라인 스케줄 실행 완료 ({}ms) ===", System.currentTimeMillis() - start);
    }
}
