package com.fundmessenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.CerebrasClient;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * S-45: CerebrasClient 우선순위 메커니즘 + 성능 프로파일링 테스트
 *
 * 테스트 항목:
 * 1. 우선순위 양보 메커니즘 (단위 테스트, API 미호출)
 * 2. 동시 요청 우선순위 (실제 API)
 * 3. 서비스별 요청 크기 프로파일링 (실제 API)
 * 4. RPM 댐퍼 정확성 (단위 테스트, API 미호출)
 * 5. getCurrentUsage() 상태 확인
 *
 * 실행: ./gradlew.bat test --tests "com.fundmessenger.CerebrasClientPerformanceTest"
 * 주의: 실제 API 호출 테스트는 CEREBRAS_API_KEYS 환경변수 필요
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CerebrasClientPerformanceTest {

    static final ObjectMapper mapper = new ObjectMapper();
    static final Path OUTPUT_DIR = Path.of("../test-ai/outputs");
    static final StringBuilder report = new StringBuilder();

    static CerebrasClient client;
    static boolean apiAvailable = false;

    @BeforeAll
    static void setup() throws Exception {
        // .env 로드
        Path envFile = Path.of("../.env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    int idx = trimmed.indexOf('=');
                    System.setProperty(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
                }
            }
        }

        Files.createDirectories(OUTPUT_DIR);

        report.append("# S-45 CerebrasClient 성능 테스트 결과\n\n");
        report.append("실행 시각: ").append(java.time.LocalDateTime.now()).append("\n\n");

        // CerebrasClient 인스턴스 생성 + Reflection으로 초기화
        client = new CerebrasClient();

        String apiKeysRaw = System.getProperty("CEREBRAS_API_KEYS",
                System.getenv("CEREBRAS_API_KEYS") != null ? System.getenv("CEREBRAS_API_KEYS") : "");

        setField(client, "apiKeysRaw", apiKeysRaw);
        setField(client, "model", "qwen-3-235b-a22b-instruct-2507");
        client.init();

        apiAvailable = client.isAvailable();
        System.out.println("=== CerebrasClient 초기화: API " + (apiAvailable ? "사용 가능" : "미설정 — API 테스트 스킵") + " ===");
        report.append("API 상태: ").append(apiAvailable ? "사용 가능" : "미설정").append("\n\n");
    }

    @AfterAll
    static void writeReport() throws IOException {
        String reportStr = report.toString();
        Files.writeString(OUTPUT_DIR.resolve("s45-perf-result.md"), reportStr);
        System.out.println("\n" + reportStr);
        System.out.println("결과 저장: " + OUTPUT_DIR.resolve("s45-perf-result.md").toAbsolutePath());
    }

    // ═══════════════════════════════════════════════
    // 테스트 1: 우선순위 양보 메커니즘 (API 미호출)
    // ═══════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[1] 우선순위 양보: pendingUserRequests > 0이면 파이프라인이 양보하는지")
    void test1_priorityYield() throws Exception {
        report.append("## [1] 우선순위 양보 메커니즘\n\n");

        // 테스트용 CerebrasClient — 실제 API 미호출, acquireKey만 테스트
        CerebrasClient testClient = new CerebrasClient();
        setField(testClient, "apiKeysRaw", "fake-key-for-test");
        setField(testClient, "model", "test");
        testClient.init();

        AtomicInteger pendingUserRequests = getField(testClient, "pendingUserRequests");
        Method acquireKey = CerebrasClient.class.getDeclaredMethod("acquireKey", int.class, boolean.class);
        acquireKey.setAccessible(true);

        // 시나리오: pendingUserRequests=1일 때 파이프라인(userPriority=false)이 양보하는지
        pendingUserRequests.set(1);

        AtomicLong pipelineStartTime = new AtomicLong();
        AtomicLong pipelineKeyAcquiredTime = new AtomicLong();
        AtomicLong yieldCount = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch pipelineStarted = new CountDownLatch(1);

        // Thread A: 파이프라인 요청 (양보해야 함)
        Future<?> pipelineFuture = executor.submit(() -> {
            try {
                pipelineStartTime.set(System.currentTimeMillis());
                pipelineStarted.countDown();
                // acquireKey 호출 — pendingUserRequests > 0이면 500ms sleep 루프
                String key = (String) acquireKey.invoke(testClient, 100, false);
                pipelineKeyAcquiredTime.set(System.currentTimeMillis());
            } catch (Exception e) {
                if (!(e.getCause() instanceof InterruptedException)) {
                    e.printStackTrace();
                }
            }
        });

        // 메인 스레드: 1.5초 후 pendingUserRequests를 0으로
        pipelineStarted.await();
        long releaseDelay = 1500;
        Thread.sleep(releaseDelay);
        pendingUserRequests.set(0);
        long releaseTime = System.currentTimeMillis();

        // 파이프라인 완료 대기 (최대 5초)
        pipelineFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        long yieldDuration = pipelineKeyAcquiredTime.get() - pipelineStartTime.get();
        boolean yielded = yieldDuration >= (releaseDelay - 200); // 200ms 허용 오차

        System.out.printf("  파이프라인 대기 시간: %dms (기대: >= %dms)%n", yieldDuration, releaseDelay);
        System.out.printf("  양보 확인: %s%n", yielded ? "✅" : "❌");

        report.append(String.format("- 파이프라인 대기 시간 (pendingUser=1): **%dms** (기대: >= %dms)%n", yieldDuration, releaseDelay));
        report.append(String.format("- 사용자 요청 해제 후 파이프라인 진행: **%s**%n", yielded ? "✅" : "❌"));
        report.append("\n");

        Assertions.assertTrue(yielded, "파이프라인이 pendingUserRequests > 0일 때 양보해야 함");
    }

    @Test
    @Order(2)
    @DisplayName("[1b] 사용자 요청(userPriority=true)은 pendingUserRequests와 무관하게 진행")
    void test1b_userPriorityNotBlocked() throws Exception {
        report.append("## [1b] 사용자 요청은 양보하지 않음\n\n");

        CerebrasClient testClient = new CerebrasClient();
        setField(testClient, "apiKeysRaw", "fake-key-for-test");
        setField(testClient, "model", "test");
        testClient.init();

        AtomicInteger pendingUserRequests = getField(testClient, "pendingUserRequests");
        Method acquireKey = CerebrasClient.class.getDeclaredMethod("acquireKey", int.class, boolean.class);
        acquireKey.setAccessible(true);

        // pendingUserRequests=5여도 사용자 요청은 즉시 진행
        pendingUserRequests.set(5);

        long start = System.currentTimeMillis();
        String key = (String) acquireKey.invoke(testClient, 100, true); // userPriority=true
        long elapsed = System.currentTimeMillis() - start;

        boolean notBlocked = elapsed < 200; // 즉시 진행
        System.out.printf("  사용자 요청 키 획득: %dms (기대: < 200ms)%n", elapsed);
        System.out.printf("  즉시 진행 확인: %s%n", notBlocked ? "✅" : "❌");

        report.append(String.format("- 사용자 요청 키 획득 시간: **%dms** (기대: < 200ms)%n", elapsed));
        report.append(String.format("- 즉시 진행 확인: **%s**%n", notBlocked ? "✅" : "❌"));
        report.append("\n");

        Assertions.assertTrue(notBlocked, "사용자 요청은 pendingUserRequests와 무관하게 즉시 진행해야 함");
    }

    // ═══════════════════════════════════════════════
    // 테스트 2: 동시 요청 우선순위 (실제 API)
    // ═══════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("[2] 동시 요청: 파이프라인 중 사용자 요청이 먼저 처리되는지")
    void test2_concurrentPriority() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");
        report.append("## [2] 동시 요청 우선순위\n\n");

        String smallSystem = "당신은 숫자 계산기입니다. 주어진 숫자에 1을 더한 결과를 JSON으로 응답하세요. {\"result\": 숫자}";

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<RequestResult> results = new CopyOnWriteArrayList<>();
        CountDownLatch allDone = new CountDownLatch(4); // 파이프라인 3건 + 사용자 1건

        // 파이프라인 3건 (일반 우선순위)
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    JsonNode res = client.generateJson(smallSystem, "숫자: " + (idx + 10));
                    long elapsed = System.currentTimeMillis() - start;
                    results.add(new RequestResult("pipeline-" + idx, false, start, elapsed, res));
                    System.out.printf("  파이프라인-%d 완료: %dms%n", idx, elapsed);
                } catch (Exception e) {
                    results.add(new RequestResult("pipeline-" + idx, false, System.currentTimeMillis(), -1, null));
                    System.out.printf("  파이프라인-%d 실패: %s%n", idx, e.getMessage());
                } finally {
                    allDone.countDown();
                }
            });
        }

        // 500ms 후 사용자 요청 1건 (우선 순위)
        Thread.sleep(500);
        executor.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                JsonNode res = client.generateJsonUser(smallSystem, "숫자: 999");
                long elapsed = System.currentTimeMillis() - start;
                results.add(new RequestResult("user-0", true, start, elapsed, res));
                System.out.printf("  사용자 요청 완료: %dms%n", elapsed);
            } catch (Exception e) {
                results.add(new RequestResult("user-0", true, System.currentTimeMillis(), -1, null));
                System.out.printf("  사용자 요청 실패: %s%n", e.getMessage());
            } finally {
                allDone.countDown();
            }
        });

        allDone.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        // 결과 분석
        RequestResult userResult = results.stream().filter(r -> r.isUser).findFirst().orElse(null);
        List<RequestResult> pipelineResults = results.stream().filter(r -> !r.isUser).toList();

        report.append("| 요청 | 우선순위 | 응답시간(ms) | 결과 |\n");
        report.append("|------|---------|-------------|------|\n");
        for (RequestResult r : results) {
            report.append(String.format("| %s | %s | %d | %s |%n",
                    r.name, r.isUser ? "사용자" : "파이프라인",
                    r.elapsedMs, r.response != null ? "✅" : "❌"));
        }

        if (userResult != null && userResult.elapsedMs > 0) {
            long avgPipeline = (long) pipelineResults.stream()
                    .filter(r -> r.elapsedMs > 0)
                    .mapToLong(r -> r.elapsedMs).average().orElse(0);
            report.append(String.format("%n- 사용자 요청 응답시간: **%dms**%n", userResult.elapsedMs));
            report.append(String.format("- 파이프라인 평균 응답시간: **%dms**%n", avgPipeline));

            // 사용자 요청이 500ms 늦게 시작했는데도 합리적인 시간 내 완료되었는지
            long userCompletionTime = userResult.startTime + userResult.elapsedMs;
            long lastPipelineCompletion = pipelineResults.stream()
                    .filter(r -> r.elapsedMs > 0)
                    .mapToLong(r -> r.startTime + r.elapsedMs).max().orElse(0);
            boolean userFinishedBeforePipeline = userCompletionTime <= lastPipelineCompletion;
            report.append(String.format("- 사용자 요청이 파이프라인 전체보다 먼저 완료: **%s**%n",
                    userFinishedBeforePipeline ? "✅" : "⚠️ (RPM 여유로 동시 처리됨)"));
        }
        report.append("\n");
    }

    // ═══════════════════════════════════════════════
    // 테스트 3: 서비스별 요청 크기 프로파일링
    // ═══════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("[3] 서비스별 프로파일: NewsPipeline L0 (뉴스 분류)")
    void test3a_profilePipelineL0() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");
        report.append("## [3] 서비스별 요청 프로파일\n\n");
        report.append("| 서비스 | 시스템프롬프트(자) | 유저프롬프트(자) | 추정토큰 | 응답시간(ms) | 결과 |\n");
        report.append("|--------|-----------------|----------------|---------|-------------|------|\n");

        // NewsPipeline Layer 0: 분류 프롬프트
        String systemPrompt = """
                당신은 금융 뉴스 분류 전문가입니다.
                주어진 뉴스 기사들을 이슈 단위로 클러스터링하세요.

                규칙:
                1. 같은 이슈를 다루는 기사들을 하나의 클러스터로 묶으세요.
                2. 투자/시장 맥락이 없는 기사는 is_investment_relevant: false로 표시하세요.
                3. 기존 canonical news와 같은 이슈라면 matched_canonical_id를 지정하세요.
                4. 새로운 이슈라면 matched_canonical_id: null로 두세요.

                JSON 형식으로 응답하세요:
                {
                  "clusters": [
                    {
                      "cluster_id": "c1",
                      "raw_news_ids": [1, 2],
                      "is_investment_relevant": true,
                      "matched_canonical_id": null,
                      "reason": "클러스터링 이유"
                    }
                  ]
                }
                """;

        // 가짜 뉴스 10건
        StringBuilder userPrompt = new StringBuilder("=== 새 기사 ===\n");
        for (int i = 1; i <= 10; i++) {
            userPrompt.append(String.format("[%d] 삼성전자 %d분기 실적 발표 — 반도체 부문 매출 %d조원 달성 | 삼성전자가 %d분기 잠정실적을 발표했다. 반도체 부문이 전분기 대비 15%% 성장했다.%n", i, i % 4 + 1, 20 + i, i % 4 + 1));
        }
        userPrompt.append("\n=== 기존 canonical news ===\n");
        for (int i = 1; i <= 5; i++) {
            userPrompt.append(String.format("[%d] 반도체 업황 회복세 %d월 전망%n", i, i));
        }

        profileAndRecord("Pipeline L0 (분류)", systemPrompt, userPrompt.toString(), false);
    }

    @Test
    @Order(5)
    @DisplayName("[3] 서비스별 프로파일: NewsPipeline L2 (신규 생성)")
    void test3b_profilePipelineL2() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");

        String systemPrompt = """
                당신은 금융 뉴스 작성 전문가입니다.
                주어진 원문 기사들을 기반으로 하나의 종합 뉴스를 markdown으로 작성하세요.
                또한 관련 종목을 태깅하고 relevance 점수(1~10)를 부여하세요.

                규칙:
                - 기사 나열이 아니라 이슈 중심으로 정리하세요.
                - tags에는 주제 태그만 넣으세요.
                - sentiment: {"type": "bullish|bearish|neutral", "score": 1~100}

                JSON 형식으로 응답:
                {"title":"제목","markdown":"본문","tags":["태그"],"sentiment":{"type":"bullish","score":75},"persona":{"name":"시장돌이","emoji":"📈"},"couplings":[{"stock_id":1,"relevance":9}]}
                """;

        StringBuilder userPrompt = new StringBuilder("=== 원문 기사들 ===\n");
        userPrompt.append("삼성전자, 3분기 매출 79조원 달성…반도체가 이끌었다\n");
        userPrompt.append("삼성전자가 2026년 3분기 잠정실적을 발표했다. 매출 79조원, 영업이익 17조원으로 시장 기대치를 상회했다.\n");
        userPrompt.append("---\n");
        userPrompt.append("SK하이닉스, HBM4 양산 본격화…엔비디아 블랙웰 공급\n");
        userPrompt.append("SK하이닉스가 HBM4 양산을 본격화하며 엔비디아 블랙웰 GPU에 독점 공급한다.\n");
        userPrompt.append("\n=== 활성 종목 목록 ===\n");
        userPrompt.append("[1] 삼성전자 (005930/KR)\n[2] SK하이닉스 (000660/KR)\n[3] LG에너지솔루션 (373220/KR)\n");

        profileAndRecord("Pipeline L2 (생성)", systemPrompt, userPrompt.toString(), false);
    }

    @Test
    @Order(6)
    @DisplayName("[3] 서비스별 프로파일: AiCommentBot (댓글 생성)")
    void test3c_profileCommentBot() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");

        String systemPrompt = """
                당신은 대학 투자 동아리의 게시판 댓글 AI입니다.
                게시글을 읽고 유용한 댓글을 작성하세요.

                규칙:
                - 투자 관점에서 건설적인 의견
                - 2-3문장으로 간결하게
                - 반말 금지, "~이다/~한다" 체

                JSON 형식: {"comment": "댓글 내용", "tone": "supportive|critical|analytical"}
                """;

        String userPrompt = """
                게시글 제목: 삼성전자 3분기 실적 분석
                게시글 내용: 삼성전자 3분기 실적이 시장 기대치를 상회했다. 반도체 부문 매출이 전분기 대비 15% 성장하며 회복세를 보였다.
                HBM 수요 증가와 AI 서버용 DRAM 출하량 확대가 주요 요인이다. 다만 파운드리 부문은 여전히 적자 지속.
                """;

        profileAndRecord("CommentBot (댓글)", systemPrompt, userPrompt, false);
    }

    @Test
    @Order(7)
    @DisplayName("[3] 서비스별 프로파일: AiDecision (의사결정서)")
    void test3d_profileDecision() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");

        String systemPrompt = """
                당신은 대학 투자동아리의 투자 분석가입니다.
                아래 종목 토론 내용을 바탕으로 투자 의사결정서를 작성하세요.

                ## 종목 정보
                - 종목: 삼성전자 (005930)
                - 시장: KR
                - 매매 유형: BUY

                ## 작성 형식
                1. 요약 (1-2문장)
                2. 투자 근거
                3. 리스크 요인
                4. 매매 전략 (진입/청산 기준)
                5. 결론

                반드시 아래 JSON 형식으로 응답하세요:
                {"title": "의사결정서 제목", "content": "마크다운 본문"}
                """;

        // 가짜 토론 메시지 (중간 크기)
        StringBuilder userPrompt = new StringBuilder();
        String[] names = {"김분석", "이투자", "박리서치", "최전략", "정퀀트"};
        String[] messages = {
                "삼성전자 3분기 실적 서프라이즈 나왔는데 추가 매수 어떻게 생각하세요?",
                "HBM 시장 점유율이 올라가고 있고 4분기 가이던스도 긍정적입니다.",
                "다만 파운드리 적자가 걱정이에요. 언제 BEP 달성할지 모르겠네요.",
                "현재가 기준 PER 15배인데 역사적 밴드 하단이라 매력적입니다.",
                "글로벌 AI 수요 감안하면 메모리 업사이클 초입이라고 봅니다. 목표가 9만원.",
                "리스크는 미국 대중 수출규제 강화 가능성. 중국 매출 비중 점검 필요.",
                "DRAM spot 가격 추이 보면 지금이 적기라고 생각합니다.",
                "동의합니다. 다만 분할매수로 리스크 관리하는게 좋겠습니다.",
                "1차 매수 비중 30%, 조정 시 추가 20% 제안합니다.",
                "종합하면 매수 의견 일치. 손절 기준은 -8%로 잡죠."
        };
        for (int i = 0; i < messages.length; i++) {
            userPrompt.append(String.format("[%s] %s%n", names[i % names.length], messages[i]));
        }

        profileAndRecord("Decision (의사결정서)", systemPrompt, userPrompt.toString(), true);
    }

    @Test
    @Order(8)
    @DisplayName("[3] 서비스별 프로파일: AiReport (운용보고서)")
    void test3e_profileReport() throws Exception {
        Assumptions.assumeTrue(apiAvailable, "API 키 미설정 — 스킵");

        String systemPrompt = """
                당신은 대학 투자동아리의 펀드 매니저입니다.
                아래 데이터를 바탕으로 종합 운용보고서를 작성하세요.

                ## 원칙
                - "~이다", "~한다" 체.
                - 한국어로 작성.
                - 마크다운 형식. 첫 줄 ## 제목. H3으로 섹션 구분.
                - 수치는 제공된 데이터를 그대로 사용. 추측/계산 금지.

                JSON으로 응답: {"title": "보고서 제목", "content": "마크다운 본문"}
                """;

        String userPrompt = """
                기간: 2026-03-10 ~ 2026-03-17

                ### 자산 변동 추이
                - 2026-03-10: KRW현금=50000000, KRW평가=120000000, 총자산KRW=170000000, 실현손익=2500000, 미실현손익=8000000
                - 2026-03-14: KRW현금=45000000, KRW평가=128000000, 총자산KRW=173000000, 실현손익=3200000, 미실현손익=9500000
                - 2026-03-17: KRW현금=42000000, KRW평가=135000000, 총자산KRW=177000000, 실현손익=4100000, 미실현손익=11000000

                ### 매매 기록
                - [BUY] 삼성전자(005930/KR) 수량=100, 체결가=72500, 일시=03-11 09:30
                - [BUY] SK하이닉스(000660/KR) 수량=50, 체결가=185000, 일시=03-13 10:15

                ### 포트폴리오 비중
                - 삼성전자(005930): 매수금액=72500000, 비중=53.7%
                - SK하이닉스(000660): 매수금액=46250000, 비중=34.3%
                - NAVER(035420): 매수금액=16250000, 비중=12.0%
                """;

        profileAndRecord("Report (운용보고서)", systemPrompt, userPrompt, true);
    }

    // ═══════════════════════════════════════════════
    // 테스트 4: RPM 댐퍼 정확성
    // ═══════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("[4] RPM 댐퍼: requestHistory 트래킹 정확성")
    void test4_rpmDamperAccuracy() throws Exception {
        report.append("\n## [4] RPM 댐퍼 정확성\n\n");

        // 마스킹 키가 겹치지 않도록 8자 이상 고유한 prefix 사용
        String key1 = "alpha-key-0001-test";
        String key2 = "bravo-key-0002-test";
        CerebrasClient testClient = new CerebrasClient();
        setField(testClient, "apiKeysRaw", key1 + "," + key2);
        setField(testClient, "model", "test");
        testClient.init();

        // recordRequest를 Reflection으로 호출하여 히스토리 채우기
        Method recordRequest = CerebrasClient.class.getDeclaredMethod("recordRequest", String.class, long.class);
        recordRequest.setAccessible(true);

        Method recordInputTokens = CerebrasClient.class.getDeclaredMethod("recordInputTokens", String.class, int.class);
        recordInputTokens.setAccessible(true);

        long now = System.currentTimeMillis();

        // 키 1에 25건 기록 (모두 window 안에 들어오도록 최근 시각 사용)
        for (int i = 0; i < 25; i++) {
            recordRequest.invoke(testClient, key1, now - (25 - i) * 100L);
            recordInputTokens.invoke(testClient, key1, 500);
        }

        // 키 2에 10건 기록
        for (int i = 0; i < 10; i++) {
            recordRequest.invoke(testClient, key2, now - (10 - i) * 100L);
            recordInputTokens.invoke(testClient, key2, 1000);
        }

        Map<String, Map<String, Object>> usage = testClient.getCurrentUsage();
        System.out.println("  현재 사용량: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(usage));

        report.append("```json\n");
        report.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(usage));
        report.append("\n```\n\n");

        // 검증: 키 1은 25/30, 키 2는 10/30
        // getCurrentUsage는 maskKey(8자+"...")로 반환하므로 alpha-ke..., bravo-ke...
        List<String> rpms = usage.values().stream()
                .map(v -> (String) v.get("rpm"))
                .sorted()
                .toList();
        System.out.println("  RPM 값들: " + rpms);

        boolean has25 = rpms.stream().anyMatch(r -> r.startsWith("25/"));
        boolean has10 = rpms.stream().anyMatch(r -> r.startsWith("10/"));

        report.append(String.format("- 키1 RPM 트래킹 (25/30): **%s**%n", has25 ? "✅" : "❌"));
        report.append(String.format("- 키2 RPM 트래킹 (10/30): **%s**%n", has10 ? "✅" : "❌"));
        report.append("\n");

        Assertions.assertTrue(has25, "키1 RPM이 25/30이어야 함 (실제: " + rpms + ")");
        Assertions.assertTrue(has10, "키2 RPM이 10/30이어야 함 (실제: " + rpms + ")");
    }

    @Test
    @Order(10)
    @DisplayName("[4b] N개 키 확장: 키 10개에서 댐퍼 + acquireKey 정상 동작")
    void test4b_nKeysScaling() throws Exception {
        report.append("## [4b] N개 키 확장 테스트\n\n");

        // 10개 키로 초기화 (향후 키 추가 시나리오 시뮬레이션)
        // maskKey()가 앞 8자+"..."로 마스킹하므로 앞 8자가 고유해야 함
        int keyCount = 10;
        String[] prefixes = {"aaaaaa00", "bbbbbb01", "cccccc02", "dddddd03", "eeeeee04",
                             "ffffff05", "gggggg06", "hhhhhh07", "iiiiii08", "jjjjjj09"};
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add(prefixes[i] + "-scale-test");
        }
        CerebrasClient testClient = new CerebrasClient();
        setField(testClient, "apiKeysRaw", String.join(",", keys));
        setField(testClient, "model", "test");
        testClient.init();

        // Reflection 준비
        Method recordRequest = CerebrasClient.class.getDeclaredMethod("recordRequest", String.class, long.class);
        recordRequest.setAccessible(true);
        Method recordInputTokens = CerebrasClient.class.getDeclaredMethod("recordInputTokens", String.class, int.class);
        recordInputTokens.setAccessible(true);
        Method acquireKeyMethod = CerebrasClient.class.getDeclaredMethod("acquireKey", int.class, boolean.class);
        acquireKeyMethod.setAccessible(true);

        long now = System.currentTimeMillis();

        // 키 0~8을 RPM 30으로 포화시키기 (9개 키 포화)
        for (int k = 0; k < keyCount - 1; k++) {
            for (int r = 0; r < 30; r++) {
                recordRequest.invoke(testClient, keys.get(k), now - r * 10L);
                recordInputTokens.invoke(testClient, keys.get(k), 100);
            }
        }
        // 키 9는 비어있음 (유일하게 사용 가능)

        // acquireKey 호출 → 포화 안된 마지막 키(scale-key-0009)가 반환되어야 함
        long start = System.currentTimeMillis();
        String acquiredKey = (String) acquireKeyMethod.invoke(testClient, 100, false);
        long elapsed = System.currentTimeMillis() - start;

        boolean gotLastKey = acquiredKey.equals(keys.get(keyCount - 1));
        System.out.printf("  10개 키 중 9개 포화 → 획득 키: %s (기대: %s), %dms%n",
                acquiredKey.substring(0, 15) + "...", keys.get(keyCount - 1).substring(0, 15) + "...", elapsed);

        // getCurrentUsage 검증 — 10개 키 모두 표시되는지
        Map<String, Map<String, Object>> usage = testClient.getCurrentUsage();
        int usageKeyCount = usage.size();
        System.out.printf("  getCurrentUsage 키 수: %d (기대: %d)%n", usageKeyCount, keyCount);

        // 총 RPM 용량 = 키 수 × 30
        int totalRpmCapacity = keyCount * 30;
        long totalRpmUsed = usage.values().stream()
                .map(v -> (String) v.get("rpm"))
                .mapToInt(rpm -> Integer.parseInt(rpm.split("/")[0]))
                .sum();
        System.out.printf("  총 RPM: %d/%d%n", totalRpmUsed, totalRpmCapacity);

        report.append(String.format("- 키 %d개 초기화: **✅**%n", keyCount));
        report.append(String.format("- 9개 포화 후 마지막 키 획득: **%s** (%dms)%n", gotLastKey ? "✅" : "❌", elapsed));
        report.append(String.format("- getCurrentUsage 키 수: **%d/%d** %s%n",
                usageKeyCount, keyCount, usageKeyCount == keyCount ? "✅" : "❌"));
        report.append(String.format("- 총 RPM: **%d/%d** (용량 %dRPM, %dK TPM)%n",
                totalRpmUsed, totalRpmCapacity, totalRpmCapacity, keyCount * 60));
        report.append("\n");

        Assertions.assertTrue(gotLastKey, "포화 안된 마지막 키가 반환되어야 함");
        Assertions.assertEquals(keyCount, usageKeyCount, "모든 키가 usage에 표시되어야 함");
    }

    @Test
    @Order(11)
    @DisplayName("[4c] 키 포화 시 대기: 모든 키 RPM 꽉 차면 대기 후 획득")
    void test4c_allKeysSaturated() throws Exception {
        report.append("## [4c] 전체 키 포화 시 대기 동작\n\n");

        // 3개 키 초기화
        List<String> keys = List.of("satur-key-0001-test", "satur-key-0002-test", "satur-key-0003-test");
        CerebrasClient testClient = new CerebrasClient();
        setField(testClient, "apiKeysRaw", String.join(",", keys));
        setField(testClient, "model", "test");
        testClient.init();

        Method recordRequest = CerebrasClient.class.getDeclaredMethod("recordRequest", String.class, long.class);
        recordRequest.setAccessible(true);
        Method recordInputTokens = CerebrasClient.class.getDeclaredMethod("recordInputTokens", String.class, int.class);
        recordInputTokens.setAccessible(true);
        Method acquireKeyMethod = CerebrasClient.class.getDeclaredMethod("acquireKey", int.class, boolean.class);
        acquireKeyMethod.setAccessible(true);

        long now = System.currentTimeMillis();

        // 모든 키를 RPM 30으로 포화 — 가장 오래된 요청이 58초 전 (2초 후 만료)
        for (String key : keys) {
            for (int r = 0; r < 30; r++) {
                recordRequest.invoke(testClient, key, now - 58_000 + r * 10L);
                recordInputTokens.invoke(testClient, key, 50);
            }
        }

        // acquireKey → 약 2초 대기 후 키 획득 예상
        long start = System.currentTimeMillis();
        String acquiredKey = (String) acquireKeyMethod.invoke(testClient, 100, false);
        long elapsed = System.currentTimeMillis() - start;

        boolean waitedAndAcquired = acquiredKey != null && elapsed >= 1500; // 최소 1.5초 대기
        System.out.printf("  전체 포화 후 대기: %dms, 획득 키: %s%n",
                elapsed, acquiredKey != null ? acquiredKey.substring(0, 12) + "..." : "null");

        report.append(String.format("- 3개 키 전체 포화 후 대기 시간: **%dms**%n", elapsed));
        report.append(String.format("- 대기 후 키 획득: **%s**%n", waitedAndAcquired ? "✅" : "❌"));
        report.append("\n");

        Assertions.assertNotNull(acquiredKey, "대기 후 키를 획득해야 함");
        Assertions.assertTrue(elapsed >= 1500, "포화 상태에서 최소 1.5초 대기해야 함 (실제: " + elapsed + "ms)");
    }

    // ═══════════════════════════════════════════════
    // 테스트 5: 토큰 추정 정확성
    // ═══════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("[5] 토큰 추정: getEstimatedTokens() 정확성")
    void test5_tokenEstimation() {
        report.append("## [5] 토큰 추정 정확성\n\n");

        // 다양한 길이의 텍스트로 추정 토큰 확인
        String[] samples = {
                "짧은 텍스트",   // ~10자
                "A".repeat(1000),  // 1000자
                "한".repeat(1000), // 한글 1000자
                "A".repeat(10000), // 10000자
        };

        report.append("| 텍스트 | 길이(자) | 추정 토큰 | 자/토큰 비율 |\n");
        report.append("|--------|---------|----------|------------|\n");

        for (String sample : samples) {
            int estimated = client.getEstimatedTokens(sample);
            double ratio = (double) sample.length() / estimated;
            report.append(String.format("| %s | %d | %d | %.2f |\n",
                    sample.length() <= 20 ? sample : sample.substring(0, 10) + "...",
                    sample.length(), estimated, ratio));
            System.out.printf("  길이=%d자, 추정=%d토큰, 비율=%.2f자/토큰%n",
                    sample.length(), estimated, ratio);
        }
        report.append("\n");
    }

    // ═══════════════════════════════════════════════
    // 테스트 6: 실제 API 사용량 확인 (getCurrentUsage)
    // ═══════════════════════════════════════════════

    @Test
    @Order(99)
    @DisplayName("[6] 테스트 종료 후 실제 API 사용량")
    void test6_finalUsage() throws Exception {
        report.append("## [6] 테스트 종료 시 API 사용량\n\n");

        if (apiAvailable) {
            Map<String, Map<String, Object>> usage = client.getCurrentUsage();
            report.append("```json\n");
            report.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(usage));
            report.append("\n```\n");
        } else {
            report.append("API 미설정 — 사용량 없음\n");
        }
    }

    // ═══════════════════════════════════════════════
    // 유틸
    // ═══════════════════════════════════════════════

    private void profileAndRecord(String serviceName, String systemPrompt, String userPrompt, boolean userPriority) {
        int estimatedTokens = client.getEstimatedTokens(systemPrompt, userPrompt);

        long start = System.currentTimeMillis();
        JsonNode result;
        if (userPriority) {
            result = client.generateJsonUser(systemPrompt, userPrompt);
        } else {
            result = client.generateJson(systemPrompt, userPrompt);
        }
        long elapsed = System.currentTimeMillis() - start;

        String status = result != null ? "✅" : "❌";
        System.out.printf("  [%s] 시스템=%d자, 유저=%d자, 추정=%d토큰, 응답=%dms, %s%n",
                serviceName, systemPrompt.length(), userPrompt.length(), estimatedTokens, elapsed, status);

        report.append(String.format("| %s | %,d | %,d | %,d | %,d | %s |%n",
                serviceName, systemPrompt.length(), userPrompt.length(), estimatedTokens, elapsed, status));
    }

    record RequestResult(String name, boolean isUser, long startTime, long elapsedMs, JsonNode response) {}

    @SuppressWarnings("unchecked")
    static <T> T getField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }

    static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
