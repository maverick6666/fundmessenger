package com.fundmessenger.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.exception.ExternalServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Cerebras Inference API 클라이언트 — v8 (대기열 + 워커)
 *
 * 구조:
 *   - PriorityBlockingQueue: 모든 요청이 들어가는 단일 대기열 (사용자 우선)
 *   - KeyWorker × N: 키당 1스레드, 자기 KeySlot 상태를 보고 대기열에서 요청을 꺼내 실행
 *   - KeySlot: 원형 버퍼 (RPM + TPM 추적)
 *
 * N개 키 = N배 처리량:
 *   - 각 워커가 독립적으로 자기 키의 한계까지 작동
 *   - 락 없음 (워커 간 공유 상태 없음)
 *   - 키 추가 = 워커 추가 (init에서 자동)
 *   - 429 시 해당 워커만 쿨다운, 나머지 계속 작동
 */
@Slf4j
@Component
public class CerebrasClient {

    private static final String BASE_URL = "https://api.cerebras.ai/v1/chat/completions";
    private static final int RPM_LIMIT = 30;
    private static final int INPUT_TPM_LIMIT = 30_000;
    private static final long WINDOW_MS = 60_000;
    private static final long DEFAULT_COOLDOWN_MS = 5_000;
    private static final double CHARS_PER_TOKEN = 1.5;
    private static final double SAFETY_MARGIN = 1.15;
    private static final int MAX_429_RETRIES = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);  // 10초 연결
        factory.setReadTimeout(120_000);     // 120초 읽기 (긴 생성 대기)
        restTemplate = new RestTemplate(factory);
    }

    @Value("${cerebras.api-keys:}")
    private String apiKeysRaw;

    @Value("${cerebras.model:qwen-3-235b-a22b-instruct-2507}")
    private String model;

    private List<String> apiKeys;
    private final PriorityBlockingQueue<ApiRequest> requestQueue = new PriorityBlockingQueue<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    private final Map<String, KeySlot> keySlots = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // ========================
    // 요청 객체
    // ========================

    static class ApiRequest implements Comparable<ApiRequest> {
        final Map<String, Object> body;
        final boolean userPriority;
        final int estimatedTokens;
        final CompletableFuture<JsonNode> future = new CompletableFuture<>();
        final long createdAt = System.currentTimeMillis();

        ApiRequest(Map<String, Object> body, int estimatedTokens, boolean userPriority) {
            this.body = body;
            this.estimatedTokens = estimatedTokens;
            this.userPriority = userPriority;
        }

        @Override
        public int compareTo(ApiRequest other) {
            // 사용자 우선 → 시간순
            if (this.userPriority != other.userPriority) {
                return this.userPriority ? -1 : 1;
            }
            return Long.compare(this.createdAt, other.createdAt);
        }
    }

    // ========================
    // KeySlot: 원형 버퍼
    // ========================

    static class KeySlot {
        private final long[] timestamps;
        private final int[] tokenCounts;
        private final int rpmLimit;
        private int head = 0;
        long cooldownUntil = 0;
        int actualTpmLimit;

        KeySlot(int rpmLimit, int tpmLimit) {
            this.rpmLimit = rpmLimit;
            this.actualTpmLimit = tpmLimit;
            this.timestamps = new long[rpmLimit];
            this.tokenCounts = new int[rpmLimit];
        }

        synchronized boolean canUse(long now, int estimatedTokens) {
            if (now < cooldownUntil) return false;
            if (timestamps[head] != 0 && now - timestamps[head] < WINDOW_MS) return false;
            return tokensInWindow(now) + estimatedTokens <= actualTpmLimit;
        }

        synchronized void record(long now, int tokens) {
            timestamps[head] = now;
            tokenCounts[head] = tokens;
            head = (head + 1) % rpmLimit;
        }

        /** 마지막 기록의 토큰을 실측치로 교정 */
        synchronized void correctLastRecord(int actualTokens) {
            int lastIdx = (head - 1 + rpmLimit) % rpmLimit;
            tokenCounts[lastIdx] = actualTokens;
        }

        synchronized long msUntilAvailable(long now, int estimatedTokens) {
            if (now < cooldownUntil) return cooldownUntil - now;

            // RPM 체크
            long rpmWait = 0;
            if (timestamps[head] != 0 && now - timestamps[head] < WINDOW_MS) {
                rpmWait = timestamps[head] + WINDOW_MS - now;
            }

            // TPM 체크 — 서버 remaining이 있으면 우선 사용
            long tpmWait = 0;
            if (serverRemainingTpm >= 0 && now < serverResetAt) {
                // 서버가 알려준 잔여로 판단
                if (estimatedTokens > serverRemainingTpm) {
                    tpmWait = serverResetAt - now;
                }
            } else {
                // 서버 정보 없으면 자체 추정
                int windowTokens = tokensInWindow(now);
                if (windowTokens + estimatedTokens > actualTpmLimit) {
                    tpmWait = findTpmWait(now, estimatedTokens);
                }
            }

            return Math.max(rpmWait, tpmWait);
        }

        synchronized int remainingRpm(long now) {
            if (now < cooldownUntil) return 0;
            int count = 0;
            for (long ts : timestamps) {
                if (ts != 0 && now - ts < WINDOW_MS) count++;
            }
            return rpmLimit - count;
        }

        synchronized long remainingTpm(long now) {
            if (now < cooldownUntil) return 0;
            return actualTpmLimit - tokensInWindow(now);
        }

        /** 서버 remaining 토큰 — 음수면 미설정 */
        volatile int serverRemainingTpm = -1;
        /** 서버 reset 시각 (epoch ms) */
        volatile long serverResetAt = 0;

        /** 서버 헤더에서 TPM 상한 + remaining 교정 + 일일 잔여 체크 */
        synchronized boolean calibrateFromHeaders(HttpHeaders headers) {
            if (headers == null) return false;
            boolean changed = false;

            // TPM 상한 보정
            String limitTpm = headers.getFirst("x-ratelimit-limit-tokens-minute");
            if (limitTpm != null) {
                try {
                    int serverLimit = Integer.parseInt(limitTpm);
                    if (serverLimit != actualTpmLimit) {
                        actualTpmLimit = serverLimit;
                        changed = true;
                    }
                } catch (NumberFormatException ignored) {}
            }

            // remaining TPM — 서버가 알려주는 실제 잔여
            String remainTpm = headers.getFirst("x-ratelimit-remaining-tokens-minute");
            if (remainTpm != null) {
                try {
                    serverRemainingTpm = Integer.parseInt(remainTpm);
                } catch (NumberFormatException ignored) {}
            }

            // reset 시각
            String resetTpm = headers.getFirst("x-ratelimit-reset-tokens-minute");
            if (resetTpm != null) {
                try {
                    long resetSec = (long) Math.ceil(Double.parseDouble(resetTpm));
                    serverResetAt = System.currentTimeMillis() + resetSec * 1000;
                } catch (NumberFormatException ignored) {}
            }

            // 일일 잔여 체크 — 0이면 reset 시간만큼 쿨다운
            String remainDay = headers.getFirst("x-ratelimit-remaining-requests-day");
            String resetDay = headers.getFirst("x-ratelimit-reset-requests-day");
            if (remainDay != null) {
                try {
                    int remaining = Integer.parseInt(remainDay);
                    if (remaining <= 0 && resetDay != null) {
                        long resetSec = (long) Math.ceil(Double.parseDouble(resetDay));
                        cooldownUntil = System.currentTimeMillis() + resetSec * 1000;
                        changed = true;
                    }
                } catch (NumberFormatException ignored) {}
            }

            return changed;
        }

        private int tokensInWindow(long now) {
            int sum = 0;
            for (int i = 0; i < rpmLimit; i++) {
                if (timestamps[i] != 0 && now - timestamps[i] < WINDOW_MS) {
                    sum += tokenCounts[i];
                }
            }
            return sum;
        }

        private long findTpmWait(long now, int neededTokens) {
            // 시각순 정렬 후 하나씩 만료시키며 공간 확인
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

    // ========================
    // 초기화 / 종료
    // ========================

    @PostConstruct
    public void init() {
        if (apiKeysRaw != null && !apiKeysRaw.isBlank()) {
            apiKeys = Arrays.stream(apiKeysRaw.split(","))
                    .map(String::trim)
                    .filter(k -> !k.isBlank())
                    .toList();
        } else {
            apiKeys = List.of();
        }

        for (String key : apiKeys) {
            KeySlot slot = new KeySlot(RPM_LIMIT, INPUT_TPM_LIMIT);
            keySlots.put(key, slot);

            Thread worker = new Thread(() -> runWorker(key, slot), "cerebras-worker-" + maskKey(key));
            worker.setDaemon(true);
            workerThreads.add(worker);
            worker.start();
        }

        log.info("Cerebras v8 초기화: {}개 키, {}개 워커, 전체 {}RPM, {}K TPM",
                apiKeys.size(), workerThreads.size(), apiKeys.size() * RPM_LIMIT,
                apiKeys.size() * INPUT_TPM_LIMIT / 1000);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        workerThreads.forEach(Thread::interrupt);
    }

    // ========================
    // 워커 루프
    // ========================

    private void runWorker(String key, KeySlot slot) {
        while (running.get()) {
            try {
                // 블로킹으로 대기열에서 꺼내기 (50ms 타임아웃)
                ApiRequest request = requestQueue.poll(50, TimeUnit.MILLISECONDS);
                if (request == null) continue;

                // 슬롯에 여유가 생길 때까지 대기
                long slotWaitStart = System.currentTimeMillis();
                boolean slotWaited = false;
                while (running.get()) {
                    long now = System.currentTimeMillis();
                    long waitMs = slot.msUntilAvailable(now, request.estimatedTokens);
                    if (waitMs <= 0) break;
                    if (!slotWaited) {
                        log.info("Cerebras [{}] 슬롯 대기 시작 (wait={}ms, est={}tok, rpm={}, tpm={})",
                                maskKey(key), waitMs, request.estimatedTokens,
                                slot.remainingRpm(now), slot.remainingTpm(now));
                        slotWaited = true;
                    }
                    Thread.sleep(Math.min(waitMs, 2000));
                }
                if (slotWaited) {
                    log.info("Cerebras [{}] 슬롯 대기 완료 ({}ms)", maskKey(key), System.currentTimeMillis() - slotWaitStart);
                }

                if (!running.get()) {
                    request.future.completeExceptionally(new InterruptedException("서버 종료"));
                    break;
                }

                slot.record(System.currentTimeMillis(), request.estimatedTokens);
                executeRequest(key, slot, request);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Cerebras 워커 {} 에러: {}", maskKey(key), e.getMessage());
            }
        }
    }

    private void executeRequest(String key, KeySlot slot, ApiRequest request) {
        int retryCount = 0;

        while (retryCount < MAX_429_RETRIES) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + key);

                long apiStart = System.currentTimeMillis();
                log.info("Cerebras [{}] API 호출 시작 (est={}tok, queueWait={}ms)",
                        maskKey(key), request.estimatedTokens, apiStart - request.createdAt);

                ResponseEntity<String> response = restTemplate.exchange(
                        BASE_URL, HttpMethod.POST,
                        new HttpEntity<>(objectMapper.writeValueAsString(request.body), headers),
                        String.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    request.future.completeExceptionally(
                            new ExternalServiceException("Cerebras", "비정상 응답: " + response.getStatusCode()));
                    return;
                }

                slot.calibrateFromHeaders(response.getHeaders());

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode usage = root.path("usage");
                int actualInput = usage.path("prompt_tokens").asInt();
                int actualOutput = usage.path("completion_tokens").asInt();
                long apiMs = System.currentTimeMillis() - apiStart;

                // 실제 토큰으로 슬롯 교정 (추정치 → 실측치)
                slot.correctLastRecord(actualInput);

                log.info("Cerebras [{}] input={}, output={}, api={}ms, remaining={}, reset={}s",
                        maskKey(key), actualInput, actualOutput, apiMs,
                        slot.serverRemainingTpm,
                        slot.serverResetAt > 0 ? (slot.serverResetAt - System.currentTimeMillis()) / 1000 : -1);

                request.future.complete(root);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    retryCount++;
                    handle429(key, slot, e);
                    // 쿨다운 대기 후 재시도
                    try {
                        long wait = slot.cooldownUntil - System.currentTimeMillis();
                        if (wait > 0) Thread.sleep(wait);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        request.future.completeExceptionally(ie);
                        return;
                    }
                    continue;
                }
                request.future.completeExceptionally(
                        new ExternalServiceException("Cerebras", "HTTP " + e.getStatusCode().value()));
                return;
            } catch (Exception e) {
                request.future.completeExceptionally(
                        new ExternalServiceException("Cerebras", e.getMessage()));
                return;
            }
        }

        request.future.completeExceptionally(
                new ExternalServiceException("Cerebras", "429 " + MAX_429_RETRIES + "회 초과"));
    }

    // ========================
    // 429 처리
    // ========================

    private void handle429(String key, KeySlot slot, HttpClientErrorException e) {
        HttpHeaders headers = e.getResponseHeaders();
        long cooldownMs = DEFAULT_COOLDOWN_MS;

        if (headers != null) {
            String retryAfter = headers.getFirst("Retry-After");
            if (retryAfter == null) retryAfter = headers.getFirst("retry-after");
            if (retryAfter != null) {
                try { cooldownMs = (long) (Double.parseDouble(retryAfter) * 1000) + 500; }
                catch (NumberFormatException ignored) {}
            }
            if (cooldownMs == DEFAULT_COOLDOWN_MS) {
                String reset = headers.getFirst("x-ratelimit-reset-tokens-minute");
                if (reset != null) {
                    try { cooldownMs = (long) Math.ceil(Double.parseDouble(reset) * 1000) + 500; }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        synchronized (slot) {
            slot.cooldownUntil = System.currentTimeMillis() + cooldownMs;
        }
        log.warn("Cerebras 429 [{}] — {}초 쿨다운", maskKey(key), cooldownMs / 1000);
    }

    // ========================
    // 유틸
    // ========================

    private int estimateInputTokens(String... texts) {
        int totalChars = 0;
        for (String t : texts) { if (t != null) totalChars += t.length(); }
        return (int) Math.ceil(totalChars / CHARS_PER_TOKEN * SAFETY_MARGIN);
    }

    @SuppressWarnings("unchecked")
    private int estimateRawBodyTokens(Map<String, Object> body) {
        try {
            Object messages = body.get("messages");
            if (messages instanceof List<?> msgList) {
                int totalChars = 0;
                for (Object msg : msgList) {
                    if (msg instanceof Map<?,?> msgMap) {
                        Object content = msgMap.get("content");
                        if (content != null) totalChars += content.toString().length();
                    }
                }
                return (int) Math.ceil(totalChars / CHARS_PER_TOKEN * SAFETY_MARGIN);
            }
        } catch (Exception ignored) {}
        return 5000;
    }

    private String maskKey(String key) {
        return key.length() > 8 ? key.substring(0, 8) + "..." : key;
    }

    public int getEstimatedTokens(String... texts) { return estimateInputTokens(texts); }
    public boolean isAvailable() { return !apiKeys.isEmpty(); }

    // ========================
    // 공개 API — 대기열에 넣고 결과 대기
    // ========================

    public JsonNode generateJson(String systemPrompt, String userPrompt) {
        return generateJson(systemPrompt, userPrompt, 16000);
    }

    public JsonNode generateJson(String systemPrompt, String userPrompt, int maxCompletionTokens) {
        return submitAndWait(buildJsonBody(systemPrompt, userPrompt, 0.3, maxCompletionTokens),
                estimateInputTokens(systemPrompt, userPrompt), false, true);
    }

    public JsonNode generateJson(String systemPrompt, String userPrompt, double temperature, int maxRetries) {
        return submitAndWait(buildJsonBody(systemPrompt, userPrompt, temperature, 16000),
                estimateInputTokens(systemPrompt, userPrompt), false, true);
    }

    public JsonNode generateJsonUser(String systemPrompt, String userPrompt) {
        return submitAndWait(buildJsonBody(systemPrompt, userPrompt, 0.3, 16000),
                estimateInputTokens(systemPrompt, userPrompt), true, true);
    }

    public JsonNode generateJsonUser(String systemPrompt, String userPrompt, double temperature, int maxRetries) {
        return submitAndWait(buildJsonBody(systemPrompt, userPrompt, temperature, 16000),
                estimateInputTokens(systemPrompt, userPrompt), true, true);
    }

    public JsonNode generateRaw(Map<String, Object> body) {
        return submitAndWait(body, estimateRawBodyTokens(body), false, false);
    }

    public JsonNode generateRawUser(Map<String, Object> body) {
        return submitAndWait(body, estimateRawBodyTokens(body), true, false);
    }

    private JsonNode submitAndWait(Map<String, Object> body, int estimatedTokens,
                                    boolean userPriority, boolean extractContent) {
        if (apiKeys.isEmpty()) { log.warn("Cerebras API 키 미설정"); return null; }

        ApiRequest request = new ApiRequest(body, estimatedTokens, userPriority);
        requestQueue.offer(request);

        try {
            JsonNode root = request.future.get(3, TimeUnit.MINUTES);
            if (root == null) return null;

            if (extractContent) {
                JsonNode choices = root.path("choices");
                if (choices.isEmpty() || choices.get(0) == null) {
                    log.error("Cerebras 응답에 choices 없음");
                    return null;
                }
                String content = choices.get(0).path("message").path("content").asText("");
                if (content.isBlank()) return null;
                try {
                    return objectMapper.readTree(content);
                } catch (Exception e) {
                    log.error("Cerebras 응답 JSON 파싱 실패: {}", content.substring(0, Math.min(100, content.length())));
                    return null;
                }
            }
            return root;
        } catch (TimeoutException e) {
            request.future.cancel(true);
            log.error("Cerebras 요청 타임아웃 (3분)");
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ExternalServiceException ese) throw ese;
            log.error("Cerebras 요청 실패: {}", e.getCause().getMessage());
            return null;
        } catch (Exception e) {
            log.error("Cerebras 요청 에러: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildJsonBody(String systemPrompt, String userPrompt,
                                               double temperature, int maxCompletionTokens) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt + "\n\n/no_think"),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", temperature,
                "max_completion_tokens", maxCompletionTokens,
                "response_format", Map.of("type", "json_object")
        );
    }

    // ========================
    // 모니터링
    // ========================

    public Map<String, Map<String, Object>> getCurrentUsage() {
        long now = System.currentTimeMillis();
        Map<String, Map<String, Object>> usage = new LinkedHashMap<>();

        for (var entry : keySlots.entrySet()) {
            KeySlot slot = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("rpm", slot.remainingRpm(now) + "/" + RPM_LIMIT);
            info.put("tpm", slot.remainingTpm(now) + "/" + slot.actualTpmLimit);
            info.put("cooldown", now < slot.cooldownUntil);
            info.put("cooldownSec", now < slot.cooldownUntil ? (slot.cooldownUntil - now) / 1000 : 0);
            usage.put(maskKey(entry.getKey()), info);
        }
        usage.put("_queue", Map.of("pending", requestQueue.size()));
        return usage;
    }
}
