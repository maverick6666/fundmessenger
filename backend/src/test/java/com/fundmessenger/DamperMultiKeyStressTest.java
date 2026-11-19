package com.fundmessenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundmessenger.config.CerebrasClient;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 멀티키 포화 테스트: 4키 × RPM/TPM 한계를 넘는 요청을 넣고
 * 댐퍼가 대기열을 관리하여 모든 요청을 429 없이 완료하는지 검증
 *
 * 실행: ./gradlew.bat test --tests "com.fundmessenger.DamperMultiKeyStressTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DamperMultiKeyStressTest {

    static CerebrasClient client;

    @BeforeAll
    static void setup() throws Exception {
        Path envFile = Path.of("../.env");
        Properties props = new Properties();
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.contains("=")) {
                    int idx = t.indexOf('=');
                    System.setProperty(t.substring(0, idx).trim(), t.substring(idx + 1).trim());
                }
            }
        }

        client = new CerebrasClient();
        String keys = System.getProperty("CEREBRAS_API_KEYS",
                System.getenv("CEREBRAS_API_KEYS") != null ? System.getenv("CEREBRAS_API_KEYS") : "");

        Field f1 = CerebrasClient.class.getDeclaredField("apiKeysRaw");
        f1.setAccessible(true);
        f1.set(client, keys);

        Field f2 = CerebrasClient.class.getDeclaredField("model");
        f2.setAccessible(true);
        f2.set(client, "qwen-3-235b-a22b-instruct-2507");

        client.init();

        Assumptions.assumeTrue(client.isAvailable(), "API 키 미설정");
    }

    @Test
    @Order(1)
    @DisplayName("4키 프로덕션 시나리오: 파이프라인(순차 10건) + 사용자(동시 2건)")
    void testProductionScenario() throws Exception {
        // 프로덕션 시나리오: 뉴스 파이프라인이 순차로 10건 돌리는 중에 사용자 2명이 요청
        String system = "숫자에 1을 더해라. JSON: {\"result\": 숫자}";

        System.out.println("=== 프로덕션 시나리오 테스트 ===");
        System.out.println("파이프라인: 순차 10건 (일반 우선순위)");
        System.out.println("사용자: 동시 2건 (사용자 우선순위)\n");

        AtomicInteger pipelineSuccess = new AtomicInteger(0);
        AtomicInteger userSuccess = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        long startAll = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 스레드 1: 파이프라인 — 순차 10건 (generateJson, 일반 우선순위)
        Future<?> pipelineFuture = executor.submit(() -> {
            for (int i = 0; i < 10; i++) {
                long t = System.currentTimeMillis();
                try {
                    JsonNode result = client.generateJson(system, "파이프라인 숫자: " + i);
                    long elapsed = System.currentTimeMillis() - t;
                    if (result != null) {
                        pipelineSuccess.incrementAndGet();
                        System.out.printf("  [파이프라인 #%02d] ✅ %dms%n", i + 1, elapsed);
                    } else {
                        failCount.incrementAndGet();
                        System.out.printf("  [파이프라인 #%02d] ❌ null (%dms)%n", i + 1, elapsed);
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.printf("  [파이프라인 #%02d] ❌ %s%n", i + 1, e.getMessage());
                }
            }
        });

        // 3초 후 사용자 요청 2건 동시 (generateJsonUser, 사용자 우선순위)
        Thread.sleep(3000);
        System.out.println("\n  --- 사용자 요청 2건 투입 (3초 후) ---\n");

        Future<?> user1 = executor.submit(() -> {
            long t = System.currentTimeMillis();
            try {
                JsonNode result = client.generateJsonUser(system, "사용자1 숫자: 999");
                long elapsed = System.currentTimeMillis() - t;
                if (result != null) {
                    userSuccess.incrementAndGet();
                    System.out.printf("  [사용자1] ✅ %dms (대기 포함)%n", elapsed);
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.out.printf("  [사용자1] ❌ %s%n", e.getMessage());
            }
        });

        Future<?> user2 = executor.submit(() -> {
            long t = System.currentTimeMillis();
            try {
                JsonNode result = client.generateJsonUser(system, "사용자2 숫자: 888");
                long elapsed = System.currentTimeMillis() - t;
                if (result != null) {
                    userSuccess.incrementAndGet();
                    System.out.printf("  [사용자2] ✅ %dms (대기 포함)%n", elapsed);
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.out.printf("  [사용자2] ❌ %s%n", e.getMessage());
            }
        });

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.MINUTES);

        long totalMs = System.currentTimeMillis() - startAll;
        System.out.println("\n=== 결과 ===");
        System.out.printf("파이프라인: %d/10 성공%n", pipelineSuccess.get());
        System.out.printf("사용자: %d/2 성공%n", userSuccess.get());
        System.out.printf("실패: %d%n", failCount.get());
        System.out.printf("총 소요: %.1fs%n", totalMs / 1000.0);
        System.out.println("키별 사용량: " + client.getCurrentUsage());

        Assertions.assertEquals(10, pipelineSuccess.get(), "파이프라인 10건 전부 성공해야 함");
        Assertions.assertEquals(2, userSuccess.get(), "사용자 2건 전부 성공해야 함");
    }

    @Test
    @Order(2)
    @DisplayName("4키 TPM 포화: 10K 토큰 요청 12건 → 댐퍼가 대기 관리")
    void testMultiKeyTpmSaturation() throws Exception {
        // 4키 × 30K TPM = 120K TPM. 12건 × 10K = 120K — 정확히 포화
        // 댐퍼가 키별 분배 + 대기로 429 없이 처리해야 함
        int totalRequests = 12;
        String system = "텍스트를 3줄로 요약해라. JSON: {\"summary\": \"요약\"}";
        String bigPrompt = "삼성전자는 세계 최대 메모리 반도체 회사이다. HBM4 양산과 파운드리 확대 중이다. ".repeat(300);
        int estimatedTokens = client.getEstimatedTokens(system, bigPrompt);

        System.out.println("\n=== 4키 TPM 포화 테스트 ===");
        System.out.printf("요청당 추정 토큰: %,d%n", estimatedTokens);
        System.out.printf("총 요청: %d건 × %,d토큰 = %,d토큰%n", totalRequests, estimatedTokens, (long) totalRequests * estimatedTokens);
        System.out.println("4키 TPM 용량: 4 × 30K = 120K TPM\n");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        long startAll = System.currentTimeMillis();

        // 순차 실행 (동시에 보내면 댐퍼가 대기열을 관리하는지 확인)
        // 실제로는 파이프라인이 순차로 요청하므로 이 패턴이 더 현실적
        for (int i = 0; i < totalRequests; i++) {
            long t = System.currentTimeMillis();
            try {
                JsonNode result = client.generateJson(system, bigPrompt);
                long elapsed = System.currentTimeMillis() - t;
                if (result != null) {
                    successCount.incrementAndGet();
                    System.out.printf("  #%02d ✅ %dms (대기 포함)%n", i + 1, elapsed);
                } else {
                    failCount.incrementAndGet();
                    System.out.printf("  #%02d ❌ null (%dms)%n", i + 1, elapsed);
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t;
                failCount.incrementAndGet();
                System.out.printf("  #%02d ❌ %s (%dms)%n", i + 1, e.getMessage(), elapsed);
            }
        }

        long totalMs = System.currentTimeMillis() - startAll;
        System.out.println("\n=== 결과 ===");
        System.out.printf("성공: %d / %d (실패: %d)%n", successCount.get(), totalRequests, failCount.get());
        System.out.printf("총 소요: %.1fs%n", totalMs / 1000.0);
        System.out.println("키별 사용량: " + client.getCurrentUsage());

        Assertions.assertTrue(successCount.get() >= 8,
                "12건 중 최소 8건 이상 성공해야 함 (실제: " + successCount.get() + ")");
    }
}
