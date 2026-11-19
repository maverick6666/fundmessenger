package com.fundmessenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Cerebras 스트레스 테스트 — 서버 독립 실행 (main 메서드)
 *
 * CerebrasClient와 동일한 워커+슬롯+댐퍼 로직을 복제하여
 * RPM/TPM 한도까지 과부하를 걸고 로그를 추적한다.
 *
 * 실행: ./gradlew test --tests "com.fundmessenger.CerebrasStressTest" -i
 * 또는: java -cp ... com.fundmessenger.CerebrasStressTest
 */
public class CerebrasStressTest {

    private static final String BASE_URL = "https://api.cerebras.ai/v1/chat/completions";
    private static final String MODEL = "qwen-3-235b-a22b-instruct-2507";
    private static final int RPM_LIMIT = 30;
    private static final int INPUT_TPM_LIMIT = 30_000;
    private static final long WINDOW_MS = 60_000;
    private static final ObjectMapper mapper = new ObjectMapper();

    // ===== 카운터 =====
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final AtomicInteger timeoutCount = new AtomicInteger(0);
    private static final AtomicInteger rateLimitCount = new AtomicInteger(0);
    private static final AtomicLong totalInputTokens = new AtomicLong(0);
    private static final AtomicLong totalOutputTokens = new AtomicLong(0);
    private static final AtomicLong totalQueueWaitMs = new AtomicLong(0);
    private static final AtomicLong totalApiMs = new AtomicLong(0);

    // ===== 슬롯 (CerebrasClient.KeySlot 복제) =====
    static class KeySlot {
        final long[] timestamps;
        final int[] tokenCounts;
        final int rpmLimit;
        int head = 0;
        long cooldownUntil = 0;
        int actualTpmLimit;

        KeySlot(int rpmLimit, int tpmLimit) {
            this.rpmLimit = rpmLimit;
            this.actualTpmLimit = tpmLimit;
            this.timestamps = new long[rpmLimit];
            this.tokenCounts = new int[rpmLimit];
        }

        synchronized long msUntilAvailable(long now, int estimatedTokens) {
            if (now < cooldownUntil) return cooldownUntil - now;
            long rpmWait = 0;
            if (timestamps[head] != 0 && now - timestamps[head] < WINDOW_MS) {
                rpmWait = timestamps[head] + WINDOW_MS - now;
            }
            long tpmWait = 0;
            int windowTokens = tokensInWindow(now);
            if (windowTokens + estimatedTokens > actualTpmLimit) {
                tpmWait = findTpmWait(now, estimatedTokens);
            }
            return Math.max(rpmWait, tpmWait);
        }

        synchronized void record(long now, int tokens) {
            timestamps[head] = now;
            tokenCounts[head] = tokens;
            head = (head + 1) % rpmLimit;
        }

        synchronized int tokensInWindow(long now) {
            int sum = 0;
            for (int i = 0; i < rpmLimit; i++) {
                if (timestamps[i] != 0 && now - timestamps[i] < WINDOW_MS) sum += tokenCounts[i];
            }
            return sum;
        }

        synchronized int remainingRpm(long now) {
            int count = 0;
            for (long ts : timestamps) {
                if (ts != 0 && now - ts < WINDOW_MS) count++;
            }
            return rpmLimit - count;
        }

        synchronized long remainingTpm(long now) {
            return actualTpmLimit - tokensInWindow(now);
        }

        private long findTpmWait(long now, int neededTokens) {
            long[][] entries = new long[rpmLimit][2];
            int count = 0;
            for (int i = 0; i < rpmLimit; i++) {
                if (timestamps[i] != 0 && now - timestamps[i] < WINDOW_MS) {
                    entries[count][0] = timestamps[i];
                    entries[count][1] = tokenCounts[i];
                    count++;
                }
            }
            Arrays.sort(entries, 0, count, Comparator.comparingLong(a -> a[0]));
            int total = tokensInWindow(now);
            for (int i = 0; i < count; i++) {
                total -= (int) entries[i][1];
                if (total + neededTokens <= actualTpmLimit) {
                    return entries[i][0] + WINDOW_MS - now;
                }
            }
            return WINDOW_MS;
        }
    }

    // ===== 요청 객체 =====
    static class TestRequest implements Comparable<TestRequest> {
        final Map<String, Object> body;
        final int estimatedTokens;
        final CompletableFuture<JsonNode> future = new CompletableFuture<>();
        final long createdAt = System.currentTimeMillis();
        final String label;

        TestRequest(Map<String, Object> body, int estimatedTokens, String label) {
            this.body = body;
            this.estimatedTokens = estimatedTokens;
            this.label = label;
        }

        @Override
        public int compareTo(TestRequest other) {
            return Long.compare(this.createdAt, other.createdAt);
        }
    }

    private static final PriorityBlockingQueue<TestRequest> queue = new PriorityBlockingQueue<>();
    private static final AtomicBoolean running = new AtomicBoolean(true);

    // ===== 워커 (CerebrasClient.runWorker 복제) =====
    private static void runWorker(String key, KeySlot slot, RestTemplate rest) {
        String keyTag = key.substring(0, 8) + "...";
        while (running.get()) {
            try {
                TestRequest req = queue.poll(100, TimeUnit.MILLISECONDS);
                if (req == null) continue;

                // 슬롯 대기
                long slotWaitStart = System.currentTimeMillis();
                while (running.get()) {
                    long now = System.currentTimeMillis();
                    long waitMs = slot.msUntilAvailable(now, req.estimatedTokens);
                    if (waitMs <= 0) break;
                    log(String.format("  [SLOT-WAIT] %s | %s | wait=%dms | rpm=%d | tpm=%d | est=%d",
                            keyTag, req.label, waitMs, slot.remainingRpm(now), slot.remainingTpm(now), req.estimatedTokens));
                    Thread.sleep(Math.min(waitMs, 2000));
                }
                long slotWaitMs = System.currentTimeMillis() - slotWaitStart;

                if (!running.get()) break;

                slot.record(System.currentTimeMillis(), req.estimatedTokens);

                // API 호출
                long apiStart = System.currentTimeMillis();
                totalRequests.incrementAndGet();

                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Authorization", "Bearer " + key);

                    ResponseEntity<String> resp = rest.exchange(
                            BASE_URL, HttpMethod.POST,
                            new HttpEntity<>(mapper.writeValueAsString(req.body), headers),
                            String.class);

                    long apiMs = System.currentTimeMillis() - apiStart;
                    totalApiMs.addAndGet(apiMs);

                    JsonNode root = mapper.readTree(resp.getBody());
                    JsonNode usage = root.path("usage");
                    int inputTok = usage.path("prompt_tokens").asInt();
                    int outputTok = usage.path("completion_tokens").asInt();
                    totalInputTokens.addAndGet(inputTok);
                    totalOutputTokens.addAndGet(outputTok);
                    totalQueueWaitMs.addAndGet(slotWaitMs);

                    JsonNode timeInfo = root.path("time_info");
                    double queueTime = timeInfo.path("queue_time").asDouble();
                    double totalTime = timeInfo.path("total_time").asDouble();

                    successCount.incrementAndGet();
                    log(String.format("  [OK] %s | %s | in=%d out=%d | slotWait=%dms | api=%dms | cerebrasQueue=%.1fs total=%.1fs",
                            keyTag, req.label, inputTok, outputTok, slotWaitMs, apiMs, queueTime, totalTime));

                    req.future.complete(root);

                } catch (HttpClientErrorException e) {
                    long apiMs = System.currentTimeMillis() - apiStart;
                    if (e.getStatusCode().value() == 429) {
                        rateLimitCount.incrementAndGet();
                        String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
                        long cooldownMs = retryAfter != null ? (long)(Double.parseDouble(retryAfter) * 1000) + 500 : 5000;
                        slot.cooldownUntil = System.currentTimeMillis() + cooldownMs;
                        log(String.format("  [429] %s | %s | cooldown=%dms | api=%dms", keyTag, req.label, cooldownMs, apiMs));
                        // 재시도: 큐에 다시 넣기
                        queue.offer(req);
                    } else {
                        failCount.incrementAndGet();
                        log(String.format("  [FAIL] %s | %s | HTTP %d | api=%dms", keyTag, req.label, e.getStatusCode().value(), apiMs));
                        req.future.completeExceptionally(e);
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    long apiMs = System.currentTimeMillis() - apiStart;
                    log(String.format("  [ERROR] %s | %s | %s | api=%dms", keyTag, req.label, e.getMessage(), apiMs));
                    req.future.completeExceptionally(e);
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // ===== 테스트 시나리오 =====

    /**
     * 과부하 테스트 — 3단계:
     * Phase 1: Layer 0 시뮬 (30건 × 4배치, 동시 투입)
     * Phase 2: Layer 1+2 시뮬 (canonical 8개 순차)
     * Phase 3: RPM/TPM 폭파 — 키 1개당 30K TPM 초과하도록 대량 요청 동시 투입
     */
    private static void simulatePipeline(List<String> keys) {
        // ======== Phase 1: Layer 0 ========
        log("========================================");
        log("=== Phase 1: Layer 0 (30건 × 4배치, 동시) ===");
        log("========================================");

        List<CompletableFuture<JsonNode>> layer0Futures = new ArrayList<>();
        for (int batch = 0; batch < 4; batch++) {
            StringBuilder rawNews = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                rawNews.append(String.format("[%d] Stock %s reported Q4 earnings beat. Revenue up 15%%. Market cap $%dB.\n",
                        batch * 30 + i, randomTicker(), (int)(Math.random() * 500)));
            }
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a news clustering analyst. Return JSON with clusters array. /no_think"),
                            Map.of("role", "user", "content", rawNews.toString())
                    ),
                    "temperature", 0.3, "max_completion_tokens", 1000,
                    "response_format", Map.of("type", "json_object")
            );
            int inputEst = (int)(rawNews.length() / 1.5 * 1.15);
            String label = "L0-batch" + (batch + 1);
            TestRequest req = new TestRequest(body, inputEst, label);
            queue.offer(req);
            layer0Futures.add(req.future);
            log(String.format("[ENQUEUE] %s | estTokens=%d", label, inputEst));
        }

        long l0Start = System.currentTimeMillis();
        for (int i = 0; i < layer0Futures.size(); i++) {
            try { layer0Futures.get(i).get(3, TimeUnit.MINUTES); }
            catch (Exception e) { timeoutCount.incrementAndGet(); log(String.format("[TIMEOUT] L0-batch%d — %s", i+1, e.getMessage())); }
        }
        log(String.format("=== Phase 1 완료: %dms ===", System.currentTimeMillis() - l0Start));
        printStatus(keys);

        // ======== Phase 2: Layer 1+2 순차 ========
        log("");
        log("========================================");
        log("=== Phase 2: Layer 1+2 × 8 canonical (순차) ===");
        log("========================================");

        long l12Start = System.currentTimeMillis();
        for (int c = 0; c < 8; c++) {
            // L1
            TestRequest l1 = new TestRequest(Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Analyze news. Return JSON with new_facts array. /no_think"),
                            Map.of("role", "user", "content", "Article about " + randomTicker() + " earnings beat expectations. Revenue grew 20%.")
                    ),
                    "temperature", 0.3, "max_completion_tokens", 1000,
                    "response_format", Map.of("type", "json_object")
            ), 200, "L1-" + c);
            queue.offer(l1);
            try { l1.future.get(3, TimeUnit.MINUTES); }
            catch (Exception e) { timeoutCount.incrementAndGet(); log("[TIMEOUT] L1-" + c); continue; }

            // L2
            TestRequest l2 = new TestRequest(Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Write a 500-word Korean financial news article. Return JSON with title, markdown, tags, sentiment. /no_think"),
                            Map.of("role", "user", "content", "Write about " + randomTicker() + " Q4 earnings. Revenue $5B, up 20%%. Stock rose 8%%.")
                    ),
                    "temperature", 0.7, "max_completion_tokens", 4000,
                    "response_format", Map.of("type", "json_object")
            ), 300, "L2-" + c);
            queue.offer(l2);
            try { l2.future.get(3, TimeUnit.MINUTES); }
            catch (Exception e) { timeoutCount.incrementAndGet(); log("[TIMEOUT] L2-" + c); }
        }
        log(String.format("=== Phase 2 완료: %dms ===", System.currentTimeMillis() - l12Start));
        printStatus(keys);

        // ======== Phase 3: RPM/TPM 폭파 ========
        log("");
        log("========================================");
        log("=== Phase 3: RPM/TPM 과부하 (키당 30K TPM 초과 목표) ===");
        log("=== 40개 요청 × ~3000 입력토큰 = ~120K 총 입력 (4키 × 30K) ===");
        log("========================================");

        // 큰 프롬프트 40개를 한꺼번에 큐에 넣음
        List<CompletableFuture<JsonNode>> stressFutures = new ArrayList<>();
        long stressStart = System.currentTimeMillis();
        for (int i = 0; i < 40; i++) {
            // ~4500자 입력 → ~3000 토큰
            StringBuilder bigPrompt = new StringBuilder();
            for (int j = 0; j < 50; j++) {
                bigPrompt.append(String.format("News %d-%d: %s reported quarterly earnings. Revenue was $%.1fB, up %.0f%% year-over-year. " +
                        "Operating margin improved to %.1f%%. The company announced a $%dM buyback program. " +
                        "Analysts expect continued growth driven by AI and cloud computing demand. Stock price target raised to $%d.\n",
                        i, j, randomTicker(), Math.random()*100, Math.random()*50, Math.random()*30,
                        (int)(Math.random()*5000), (int)(Math.random()*500)));
            }
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Summarize all news into a single JSON object with 'summary' field (max 100 words). /no_think"),
                            Map.of("role", "user", "content", bigPrompt.toString())
                    ),
                    "temperature", 0.3, "max_completion_tokens", 500,
                    "response_format", Map.of("type", "json_object")
            );
            int inputEst = (int)(bigPrompt.length() / 1.5 * 1.15);
            String label = "STRESS-" + i;
            TestRequest req = new TestRequest(body, inputEst, label);
            queue.offer(req);
            stressFutures.add(req.future);
            if (i % 10 == 0) log(String.format("[ENQUEUE] %s | estTokens=%d (batch of 10)", label, inputEst));
        }
        log(String.format("[ENQUEUE] 40개 요청 투입 완료 (%dms)", System.currentTimeMillis() - stressStart));

        // 결과 대기
        for (int i = 0; i < stressFutures.size(); i++) {
            try { stressFutures.get(i).get(5, TimeUnit.MINUTES); }
            catch (Exception e) {
                timeoutCount.incrementAndGet();
                log(String.format("[TIMEOUT] STRESS-%d — %s", i, e.getMessage()));
            }
        }
        log(String.format("=== Phase 3 완료: %dms ===", System.currentTimeMillis() - stressStart));
    }

    private static String randomTicker() {
        String[] tickers = {"AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "GOOG", "META", "AMD", "INTC", "QCOM"};
        return tickers[(int)(Math.random() * tickers.length)];
    }

    private static void printStatus(List<String> keys) {
        log("--- Status ---");
        log(String.format("  requests=%d | success=%d | fail=%d | timeout=%d | 429=%d",
                totalRequests.get(), successCount.get(), failCount.get(), timeoutCount.get(), rateLimitCount.get()));
        log(String.format("  inputTokens=%d | outputTokens=%d | avgSlotWait=%dms | avgApi=%dms",
                totalInputTokens.get(), totalOutputTokens.get(),
                successCount.get() > 0 ? totalQueueWaitMs.get() / successCount.get() : 0,
                successCount.get() > 0 ? totalApiMs.get() / successCount.get() : 0));
        log("--------------");
    }

    private static void log(String msg) {
        System.out.printf("[%tT.%<tL] %s%n", System.currentTimeMillis(), msg);
    }

    // ===== main =====
    public static void main(String[] args) {
        // .env에서 키 로드
        String keysRaw = System.getenv("CEREBRAS_API_KEYS");
        if (keysRaw == null || keysRaw.isBlank()) {
            System.err.println("CEREBRAS_API_KEYS 환경변수가 없습니다. 쉼표로 구분한 키를 넣고 다시 실행하세요.");
            return;
        }

        List<String> keys = Arrays.stream(keysRaw.split(",")).map(String::trim).filter(k -> !k.isBlank()).toList();
        log("=== Cerebras 스트레스 테스트 시작 ===");
        log(String.format("키 %d개, RPM %d/키, TPM %d/키", keys.size(), RPM_LIMIT, INPUT_TPM_LIMIT));

        // RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        RestTemplate rest = new RestTemplate(factory);

        // 워커 시작
        List<Thread> workers = new ArrayList<>();
        Map<String, KeySlot> slots = new LinkedHashMap<>();
        for (String key : keys) {
            KeySlot slot = new KeySlot(RPM_LIMIT, INPUT_TPM_LIMIT);
            slots.put(key, slot);
            Thread t = new Thread(() -> runWorker(key, slot, rest), "worker-" + key.substring(0, 8));
            t.setDaemon(true);
            t.start();
            workers.add(t);
        }

        long testStart = System.currentTimeMillis();

        // 파이프라인 시뮬레이션 실행
        simulatePipeline(keys);

        // 종료
        running.set(false);
        long elapsed = System.currentTimeMillis() - testStart;

        log("");
        log("========================================");
        log("=== 최종 결과 ===");
        log("========================================");
        printStatus(keys);
        log(String.format("총 소요: %dms (%.1f초)", elapsed, elapsed / 1000.0));
        log("========================================");
    }
}
