package com.fundmessenger.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gemini API 클라이언트 — v3 (대기열 + 워커 + 모델 폴백)
 *
 * 핵심 발견 (2026-03-17):
 *   - rate limit은 키 단위가 아니라 프로젝트 내 모델 단위
 *   - 키 1개로 flash-lite(10RPM/20RPD) + flash(5RPM/20RPD) + 3-flash(5RPM/20RPD) 사용 가능
 *   - RPD는 PT 자정(KST 16:00) 고정 리셋, RPM/TPM은 롤링 윈도우
 *   - 서버가 rate limit 헤더를 제공하지 않음 → 원형 버퍼만으로 추적
 *
 * 구조:
 *   - LinkedBlockingQueue: 모든 요청이 들어가는 대기열
 *   - KeyWorker × N: 키당 1스레드
 *   - ModelSlot × 3/키: 모델별 RPM 원형 버퍼 + RPD 카운터
 *   - 폴백: flash-lite → 2.5-flash → 3-flash (RPM/RPD 소진 시 자동 전환)
 *
 * 용량 (키 4개):
 *   - RPM: 4 × (10+5+5) = 80 RPM
 *   - RPD: 4 × (20+20+20) = 240건/일
 */
@Slf4j
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final long WINDOW_MS = 60_000;
    private static final long DEFAULT_COOLDOWN_MS = 60_000;
    private static final int MAX_429_RETRIES = 5;
    private static final ZoneId PT_ZONE = ZoneId.of("America/Los_Angeles");

    /** 모델 우선순위 (빠른 순) */
    private static final ModelConfig[] MODELS = {
            new ModelConfig("gemini-2.5-flash-lite", 10, 20),
            new ModelConfig("gemini-2.5-flash", 5, 20),
            new ModelConfig("gemini-3-flash-preview", 5, 20),
    };

    record ModelConfig(String name, int rpm, int rpd) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        restTemplate = new RestTemplate(factory);
    }

    @Value("${gemini.api-keys:}")
    private String apiKeysRaw;

    private List<String> apiKeys;
    private final LinkedBlockingQueue<ApiRequest> requestQueue = new LinkedBlockingQueue<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    /** 키 → 모델별 슬롯 */
    private final Map<String, List<ModelSlot>> keyModelSlots = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // ========================
    // 요청 객체
    // ========================

    static class ApiRequest {
        final Map<String, Object> bodyTemplate; // model 필드 제외한 body
        final CompletableFuture<String> future = new CompletableFuture<>();

        ApiRequest(Map<String, Object> bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
        }
    }

    // ========================
    // ModelSlot: RPM 원형 버퍼 + RPD 카운터
    // ========================

    static class ModelSlot {
        final String modelName;
        final int rpmLimit;
        final int rpdLimit;

        // RPM: 원형 버퍼
        private final long[] timestamps;
        private int head = 0;
        long cooldownUntil = 0;

        // RPD: 단순 카운터 (PT 자정 리셋)
        int dailyCount = 0;
        LocalDate lastResetDate;

        ModelSlot(ModelConfig config) {
            this.modelName = config.name;
            this.rpmLimit = config.rpm;
            this.rpdLimit = config.rpd;
            this.timestamps = new long[config.rpm];
            this.lastResetDate = LocalDate.now(PT_ZONE);
        }

        synchronized boolean canUse(long now) {
            checkDailyReset();
            if (now < cooldownUntil) return false;
            if (dailyCount >= rpdLimit) return false;
            return timestamps[head] == 0 || now - timestamps[head] >= WINDOW_MS;
        }

        synchronized void record(long now) {
            checkDailyReset();
            timestamps[head] = now;
            head = (head + 1) % rpmLimit;
            dailyCount++;
        }

        synchronized long msUntilAvailable(long now) {
            checkDailyReset();
            if (dailyCount >= rpdLimit) return Long.MAX_VALUE; // RPD 소진 → 이 모델 사용 불가
            if (now < cooldownUntil) return cooldownUntil - now;
            if (timestamps[head] == 0) return 0;
            long expiry = timestamps[head] + WINDOW_MS;
            return expiry > now ? expiry - now : 0;
        }

        synchronized int remainingRpm(long now) {
            if (now < cooldownUntil) return 0;
            int count = 0;
            for (long ts : timestamps) {
                if (ts != 0 && now - ts < WINDOW_MS) count++;
            }
            return rpmLimit - count;
        }

        synchronized int remainingRpd() {
            checkDailyReset();
            return rpdLimit - dailyCount;
        }

        private void checkDailyReset() {
            LocalDate today = LocalDate.now(PT_ZONE);
            if (!today.equals(lastResetDate)) {
                dailyCount = 0;
                lastResetDate = today;
            }
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
            List<ModelSlot> slots = new ArrayList<>();
            for (ModelConfig mc : MODELS) {
                slots.add(new ModelSlot(mc));
            }
            keyModelSlots.put(key, slots);

            Thread worker = new Thread(() -> runWorker(key, slots), "gemini-worker-" + maskKey(key));
            worker.setDaemon(true);
            workerThreads.add(worker);
            worker.start();
        }

        int totalRpm = apiKeys.size() * Arrays.stream(MODELS).mapToInt(ModelConfig::rpm).sum();
        int totalRpd = apiKeys.size() * Arrays.stream(MODELS).mapToInt(ModelConfig::rpd).sum();
        log.info("Gemini v3 초기화: {}키, {}워커, 전체 {}RPM, {}RPD/일, 모델 {}종",
                apiKeys.size(), workerThreads.size(), totalRpm, totalRpd, MODELS.length);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        workerThreads.forEach(Thread::interrupt);
    }

    // ========================
    // 워커 루프
    // ========================

    private void runWorker(String key, List<ModelSlot> slots) {
        while (running.get()) {
            try {
                // 블로킹으로 대기열에서 꺼내기 (100ms 타임아웃)
                ApiRequest request = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                if (request == null) continue;

                // 모델 우선순위대로 사용 가능한 슬롯 찾기 + 대기
                ModelSlot available = waitForAvailableModel(slots, request);

                if (available == null) {
                    // 모든 모델 RPD 소진 → 즉시 에러 반환
                    request.future.completeExceptionally(
                            new RuntimeException("Gemini 일일 한도 소진 (모든 모델 RPD 0). KST 16:00 리셋."));
                    continue;
                }

                available.record(System.currentTimeMillis());
                executeRequest(key, slots, available, request);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Gemini 워커 {} 에러: {}", maskKey(key), e.getMessage());
            }
        }
    }

    /** 사용 가능한 모델이 생길 때까지 대기. RPD 전부 소진이면 null 반환 */
    private ModelSlot waitForAvailableModel(List<ModelSlot> slots, ApiRequest request) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000; // 최대 2분 대기

        while (running.get() && System.currentTimeMillis() < deadline) {
            long now = System.currentTimeMillis();
            boolean allRpdExhausted = true;
            long shortestWait = Long.MAX_VALUE;

            for (ModelSlot slot : slots) {
                if (slot.remainingRpd() > 0) allRpdExhausted = false;
                if (slot.canUse(now)) return slot;
                long wait = slot.msUntilAvailable(now);
                if (wait < Long.MAX_VALUE) shortestWait = Math.min(shortestWait, wait);
            }

            if (allRpdExhausted) return null; // 즉시 에러

            Thread.sleep(Math.min(shortestWait, 2000));
        }
        return null;
    }

    private void executeRequest(String key, List<ModelSlot> allSlots, ModelSlot slot, ApiRequest request) {
        int totalRetries = 0;
        ModelSlot currentSlot = slot;

        while (totalRetries < MAX_429_RETRIES) {
            try {
                String url = BASE_URL + currentSlot.modelName + ":generateContent?key=" + key;

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST,
                        new HttpEntity<>(objectMapper.writeValueAsString(request.bodyTemplate), headers),
                        String.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    request.future.completeExceptionally(new RuntimeException("Gemini 비정상 응답: " + response.getStatusCode()));
                    return;
                }

                // 폴백 모델이면 성공 시에만 record (RPD 카운트)
                if (currentSlot != slot) {
                    currentSlot.record(System.currentTimeMillis());
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isEmpty() || candidates.get(0) == null) {
                    request.future.completeExceptionally(new RuntimeException("Gemini 응답에 candidates 없음"));
                    return;
                }
                String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");

                JsonNode usage = root.path("usageMetadata");
                if (!usage.isMissingNode()) {
                    log.info("Gemini [{}][{}] input={}, output={}, rpd잔여={}/{}",
                            maskKey(key), currentSlot.modelName,
                            usage.path("promptTokenCount").asInt(),
                            usage.path("candidatesTokenCount").asInt(),
                            currentSlot.remainingRpd(), currentSlot.rpdLimit);
                }

                request.future.complete(text);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    totalRetries++;
                    synchronized (currentSlot) {
                        currentSlot.cooldownUntil = System.currentTimeMillis() + DEFAULT_COOLDOWN_MS;
                    }
                    log.warn("Gemini 429 [{}][{}] — 폴백 시도 (rpd잔여={})",
                            maskKey(key), currentSlot.modelName, currentSlot.remainingRpd());

                    // 429 → 다음 폴백 모델 찾기 (record는 성공 시에만)
                    ModelSlot nextSlot = findNextAvailableModel(allSlots, currentSlot);
                    if (nextSlot != null) {
                        currentSlot = nextSlot;
                        log.info("Gemini 폴백: {} → {}", slot.modelName, currentSlot.modelName);
                        continue;
                    }
                    // 폴백 모델도 없으면 쿨다운 대기 후 재시도
                    try { Thread.sleep(DEFAULT_COOLDOWN_MS); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        request.future.completeExceptionally(ie);
                        return;
                    }
                    continue;
                }
                log.warn("Gemini [{}][{}] HTTP {}", maskKey(key), currentSlot.modelName, e.getStatusCode().value());
                request.future.completeExceptionally(e);
                return;
            } catch (Exception e) {
                log.warn("Gemini [{}][{}] 실패: {}", maskKey(key), currentSlot.modelName, e.getMessage());
                request.future.completeExceptionally(e);
                return;
            }
        }

        request.future.completeExceptionally(
                new RuntimeException("Gemini 429 " + MAX_429_RETRIES + "회 초과 (폴백 포함)"));
    }

    /** 현재 모델 다음 우선순위에서 사용 가능한 모델 찾기 */
    private ModelSlot findNextAvailableModel(List<ModelSlot> slots, ModelSlot current) {
        boolean foundCurrent = false;
        long now = System.currentTimeMillis();
        for (ModelSlot slot : slots) {
            if (slot == current) { foundCurrent = true; continue; }
            if (foundCurrent && slot.canUse(now)) return slot;
        }
        // 현재 모델보다 앞 순위도 확인 (쿨다운 풀렸을 수 있음)
        for (ModelSlot slot : slots) {
            if (slot == current) break;
            if (slot.canUse(now)) return slot;
        }
        return null;
    }

    private String maskKey(String key) {
        return key.length() > 8 ? key.substring(0, 8) + "..." : key;
    }

    // ========================
    // 공개 API
    // ========================

    public String generateWithPdf(byte[] pdfBytes, String textPrompt, int maxRetries) {
        if (apiKeys.isEmpty()) { log.warn("Gemini API 키 미설정"); return null; }

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of(
                "inline_data", Map.of(
                        "mime_type", "application/pdf",
                        "data", Base64.getEncoder().encodeToString(pdfBytes)
                )
        ));
        parts.add(Map.of("text", textPrompt));

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("maxOutputTokens", 16000)
        );

        return submitAndWait(body);
    }

    public String generateText(String prompt, int maxRetries) {
        if (apiKeys.isEmpty()) { log.warn("Gemini API 키 미설정"); return null; }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("maxOutputTokens", 16000)
        );

        return submitAndWait(body);
    }

    private String submitAndWait(Map<String, Object> body) {
        ApiRequest request = new ApiRequest(body);
        requestQueue.offer(request);

        try {
            return request.future.get(3, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            request.future.cancel(true);
            log.error("Gemini 요청 타임아웃 (3분)");
            return null;
        } catch (ExecutionException e) {
            log.error("Gemini 요청 실패: {}", e.getCause().getMessage());
            return null;
        } catch (Exception e) {
            log.error("Gemini 요청 에러: {}", e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() { return !apiKeys.isEmpty(); }

    // ========================
    // 모니터링
    // ========================

    public Map<String, Map<String, Object>> getCurrentUsage() {
        long now = System.currentTimeMillis();
        Map<String, Map<String, Object>> usage = new LinkedHashMap<>();

        for (var entry : keyModelSlots.entrySet()) {
            for (ModelSlot slot : entry.getValue()) {
                String label = maskKey(entry.getKey()) + "/" + slot.modelName;
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("rpm", slot.remainingRpm(now) + "/" + slot.rpmLimit);
                info.put("rpd", slot.remainingRpd() + "/" + slot.rpdLimit);
                info.put("cooldown", now < slot.cooldownUntil);
                usage.put(label, info);
            }
        }
        usage.put("_queue", Map.of("pending", requestQueue.size()));
        return usage;
    }
}
