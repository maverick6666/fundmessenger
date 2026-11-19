package com.fundmessenger.jongtobang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 뉴스 파이프라인 서비스 (club_id 독립)
 * 4단계 처리: Layer 0(클러스터링) → Layer 1(분석) → Layer 2(생성/커플링) → Layer 3(검증)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPipelineService {

    private final NewsCollectorService collectorService;
    private final CerebrasClient cerebrasClient;
    private final StockPriceService stockPriceService;

    private final RawNewsRepository rawNewsRepository;
    private final NewsClusterRepository newsClusterRepository;
    private final CanonicalNewsRepository canonicalNewsRepository;
    private final StockNewsCouplingRepository couplingRepository;
    private final StockRepository stockRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantLock pipelineLock = new ReentrantLock();

    /** 한 번의 파이프라인 실행에서 처리할 미처리 raw news 최대 건수 */
    private static final int MAX_UNPROCESSED = 100;

    /** Layer 0 매칭 윈도우: 최근 N일 이내 canonical만 매칭 대상 */
    private static final int MATCHING_WINDOW_DAYS = 7;
    /** Layer 0 매칭 윈도우: 최대 건수 상한 */
    private static final int MATCHING_WINDOW_CAP = 200;
    /** Layer 2 본문 프롬프트 길이 상한 (앞 3K + 뒤 5K로 truncate) */
    private static final int BODY_LENGTH_CAP = 8000;
    private static final int GLOBAL_STOCK_CONTEXT_LIMIT = 80;
    private static final int CLUSTER_STOCK_CONTEXT_LIMIT = 16;
    private static final int HOT_STOCK_TOP_UP_LIMIT = 6;
    private static final BigDecimal HOT_STOCK_CHANGE_RATE = new BigDecimal("15");
    /** 같은 canonical에 후속 업데이트를 다시 하려면 최소 이 시간이 지나야 함 */
    private static final int MIN_UPDATE_INTERVAL_HOURS = 6;

    /**
     * 페르소나 풀 — origin-news-pipeline.md 기준 10명.
     * [name, emoji, 성격, 전문영역, 기본감성]
     * 기본감성: 해당 페르소나가 자연스럽게 쓰는 관점 (bullish/bearish/neutral)
     */
    private static final String[][] PERSONA_POOL = {
            {"시장돌이", "📈", "낙관적, 모멘텀 중시", "호재, 실적", "bullish"},
            {"곰탱이", "🐻", "보수적, 리스크 경계", "악재, 지정학", "bearish"},
            {"칩덕후", "🔬", "반도체 오타쿠", "반도체, HBM, 파운드리", "neutral"},
            {"매크로봇", "🤖", "데이터 중심, 건조함", "환율, 금리, 매크로", "neutral"},
            {"배터리언니", "🔋", "친근함, 신산업 관심", "배터리, ESS, 전기차", "bullish"},
            {"차트장인", "📊", "분석적, 기술적", "차트 많은 뉴스", "neutral"},
            {"월가통신", "🗽", "글로벌 시각", "해외 뉴스, IPO", "neutral"},
            {"가치투자왕", "👑", "장기 관점, 펀더멘털", "기업분석, 밸류에이션", "bullish"},
            {"뉴스요정", "🧚", "가볍고 캐주얼", "중립 뉴스, 이벤트", "neutral"},
            {"센티멘탈리스트", "🎭", "심리/수급 분석", "투자심리, 수급", "bearish"},
    };

    /** 감성 목표 비율: bullish 40%, bearish 30%, neutral 30% */
    private static final Map<String, Double> SENTIMENT_TARGET = Map.of(
            "bullish", 0.40,
            "bearish", 0.30,
            "neutral", 0.30
    );

    /**
     * 시스템 수준 페르소나+감성 지정.
     * 1) 감성 분포를 확인 → 목표 대비 가장 부족한 감성 결정
     * 2) 그 감성에 맞는 페르소나 중 가장 적게 쓰인 것 선택
     *
     * 반환: [name, emoji, 성격, 전문영역, 지정감성]
     */
    private String[] pickNextPersona() {
        List<CanonicalNews> allNews = canonicalNewsRepository.findAll();
        long total = Math.max(allNews.size(), 1);

        // 1) 현재 감성 분포
        Map<String, Long> sentimentCounts = new HashMap<>();
        sentimentCounts.put("bullish", 0L);
        sentimentCounts.put("bearish", 0L);
        sentimentCounts.put("neutral", 0L);
        for (CanonicalNews cn : allNews) {
            String st = cn.getSentimentType();
            if (st != null && sentimentCounts.containsKey(st)) {
                sentimentCounts.merge(st, 1L, Long::sum);
            }
        }

        // 2) 목표 대비 가장 부족한 감성 찾기
        String needSentiment = "neutral";
        double maxDeficit = -1;
        for (var entry : SENTIMENT_TARGET.entrySet()) {
            double currentRatio = (double) sentimentCounts.getOrDefault(entry.getKey(), 0L) / total;
            double deficit = entry.getValue() - currentRatio;
            if (deficit > maxDeficit) {
                maxDeficit = deficit;
                needSentiment = entry.getKey();
            }
        }

        // 3) 해당 감성에 맞는 페르소나 중 가장 적게 쓰인 것
        Map<String, Long> personaCounts = new HashMap<>();
        for (String[] p : PERSONA_POOL) personaCounts.put(p[0], 0L);
        for (CanonicalNews cn : allNews) {
            String name = cn.getAuthorName();
            if (name != null && personaCounts.containsKey(name)) {
                personaCounts.merge(name, 1L, Long::sum);
            }
        }

        // 해당 감성의 페르소나 후보
        String[] best = null;
        long bestCount = Long.MAX_VALUE;
        for (String[] p : PERSONA_POOL) {
            if (p[4].equals(needSentiment)) {
                long c = personaCounts.getOrDefault(p[0], 0L);
                if (c < bestCount) {
                    bestCount = c;
                    best = p;
                }
            }
        }

        // 해당 감성에 맞는 페르소나가 없으면 (모두 소진) 전체에서 가장 적은 것
        if (best == null) {
            for (String[] p : PERSONA_POOL) {
                long c = personaCounts.getOrDefault(p[0], 0L);
                if (c < bestCount) {
                    bestCount = c;
                    best = p;
                }
            }
        }

        log.info("감성 분포: bullish={}, bearish={}, neutral={} → 부족한 감성: {} → 페르소나: {}",
                sentimentCounts.get("bullish"), sentimentCounts.get("bearish"),
                sentimentCounts.get("neutral"), needSentiment, best != null ? best[0] : "?");

        return best != null ? best : PERSONA_POOL[0];
    }

    /**
     * 전체 파이프라인 실행 (자동/수동 공통)
     */
    @Transactional
    public boolean runPipeline() {
        if (!pipelineLock.tryLock()) {
            log.warn("News pipeline is already running, skipping duplicate trigger");
            return false;
        }

        try {
            runPipelineInternal();
            return true;
        } finally {
            pipelineLock.unlock();
        }
    }

    private void runPipelineInternal() {
        long pipelineStart = System.currentTimeMillis();
        log.info("=== 뉴스 파이프라인 시작 (전체) ===");

        // 1. 뉴스 수집 (전체 1회)
        long stepStart = System.currentTimeMillis();
        int collected = collectorService.collectNews();
        log.info("수집 완료: {}건 ({}ms)", collected, System.currentTimeMillis() - stepStart);

        // 2. 미처리 raw news 조회 (최신순, 상한 적용)
        List<RawNews> allUnprocessed = rawNewsRepository.findByProcessedFalseOrderByPublishedAtDesc();
        List<RawNews> unprocessed = allUnprocessed.size() > MAX_UNPROCESSED
                ? allUnprocessed.subList(0, MAX_UNPROCESSED) : allUnprocessed;

        if (unprocessed.isEmpty()) {
            log.info("No unprocessed raw news found, skipping pipeline");
            return;
        }

        log.info("Unprocessed raw news: total={}, batch={}", allUnprocessed.size(), unprocessed.size());

        if (!cerebrasClient.isAvailable()) {
            log.warn("Cerebras API unavailable, skipping AI news pipeline");
            return;
        }

        // 2.5. 종목 컨텍스트 구성 (후보 종목 ~100~150건)
        stepStart = System.currentTimeMillis();
        List<Stock> candidateStocks = stockPriceService.collectCandidateStocks();
        List<Stock> activeStocks = stockRepository.findByActiveTrueAndEtfFalseOrderByStockNameAsc();
        String defaultStockList = buildStockContext(candidateStocks, "ranked_candidate", GLOBAL_STOCK_CONTEXT_LIMIT);
        String stockList = defaultStockList;
        log.info("뉴스 파이프라인 후보 종목 {}건, 종목 컨텍스트 {}자 구성 완료 ({}ms)",
                candidateStocks.size(), stockList.length(), System.currentTimeMillis() - stepStart);

        // 3. Layer 0: 클러스터링 (이슈별 그룹핑 + 기존 canonical 매칭)
        //    매칭 윈도우: 최근 7일 + 200건 (findAll() 방지)
        stepStart = System.currentTimeMillis();
        LocalDateTime matchingSince = LocalDateTime.now().minusDays(MATCHING_WINDOW_DAYS);
        List<CanonicalNews> existingCanonicals = canonicalNewsRepository
                .findRecentForMatching(matchingSince, PageRequest.of(0, MATCHING_WINDOW_CAP));
        log.info("Layer 0 매칭 윈도우: {}일, {}건 (전체: {}건)", MATCHING_WINDOW_DAYS,
                existingCanonicals.size(), canonicalNewsRepository.count());
        List<ClusterInfo> clusters = new ArrayList<>();

        // 30건 단위 배치로 Layer 0 처리 (프롬프트 크기 축소 → Cerebras 타임아웃 방지)
        int batchSize = 30;
        for (int i = 0; i < unprocessed.size(); i += batchSize) {
            List<RawNews> batch = unprocessed.subList(i, Math.min(i + batchSize, unprocessed.size()));
            log.info("Layer 0 batch {}/{}: {} items",
                    (i / batchSize) + 1,
                    (int) Math.ceil((double) unprocessed.size() / batchSize),
                    batch.size());

            Map<String, Object> layer0Result = runLayer0(batch, existingCanonicals);
            if (layer0Result == null) {
                log.warn("Layer 0 batch failed, skipping batch");
                continue;
            }

            // Layer 0 결과로 클러스터 생성 + raw news 연결
            List<ClusterInfo> batchClusters = processLayer0Result(layer0Result, batch);
            clusters.addAll(batchClusters);

            // 기존 canonical 목록 갱신 (새로 생성된 것 반영)
            existingCanonicals = canonicalNewsRepository
                    .findRecentForMatching(matchingSince, PageRequest.of(0, MATCHING_WINDOW_CAP));
        }

        long layer0Ms = System.currentTimeMillis() - stepStart;
        log.info("Layer 0 완료: {}개 클러스터, {}ms", clusters.size(), layer0Ms);

        // 5. 각 canonical 업데이트/신규 생성: Layer 1(분석) → Layer 2(생성/커플링)
        stepStart = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;
        for (ClusterInfo ci : clusters) {
            String clusterStockList = buildClusterStockContext(ci.rawTexts, candidateStocks, activeStocks, defaultStockList);
            if (ci.matchedCanonicalId != null) {
                CanonicalNews existing = canonicalNewsRepository.findById(ci.matchedCanonicalId).orElse(null);
                if (existing == null) {
                    log.warn("Matched canonical {} missing in DB, falling back to new canonical flow", ci.matchedCanonicalId);
                    ci.matchedCanonicalId = null;
                }
                if (existing != null) {
                    // 가드 1: 최소 업데이트 간격 — 너무 자주 갱신하면 중복 내용 발생
                    if (existing.getUpdatedAt() != null &&
                            existing.getUpdatedAt().plusHours(MIN_UPDATE_INTERVAL_HOURS).isAfter(LocalDateTime.now())) {
                        log.info("canonical {} 최근 {}시간 내 업데이트됨, 후속 업데이트 스킵",
                                existing.getId(), MIN_UPDATE_INTERVAL_HOURS);
                        continue;
                    }

                    ci.layer2BeforeMarkdown = existing.getBodyMarkdown();
                    JsonNode analysis = runLayer1(existing, ci.rawTexts);
                    ci.layer1Analysis = analysis;

                    // 가드 2: Layer 1에서 new_facts가 없으면 후속 업데이트 불필요
                    if (analysis != null && isAllAlreadyCovered(analysis)) {
                        log.info("canonical {} — 새로운 사실 없음, 후속 업데이트 스킵", existing.getId());
                        continue;
                    }

                    boolean ok = updateCanonical(existing, ci, analysis, clusterStockList);
                    if (ok) successCount++; else failCount++;
                    continue;
                }
            }
            if (ci.isInvestmentRelevant) {
                boolean ok = createNewCanonical(ci, clusterStockList);
                if (ok) successCount++; else failCount++;
            }
        }
        long layer12Ms = System.currentTimeMillis() - stepStart;
        log.info("Layer 1+2 완료: 성공={}, 실패={}, 총={}, {}ms", successCount, failCount, clusters.size(), layer12Ms);

        // 6. Layer 3: 검증 (현재 비활성화 — Round 2~4에서 100% 실패하여 threshold 도입 전까지 비활성화)
        long layer3Ms = 0;
        log.info("Layer 3: 비활성화 (0ms)");

        // 7. raw news 처리 완료 표시
        for (RawNews raw : unprocessed) {
            raw.setProcessed(true);
        }
        rawNewsRepository.saveAll(unprocessed);

        long totalMs = System.currentTimeMillis() - pipelineStart;
        log.info("=== 뉴스 파이프라인 완료 (전체) — {}ms (수집: {}ms, L1+2: {}ms, L3: {}ms) ===",
                totalMs, layer0Ms, layer12Ms, layer3Ms);
    }

    // ========================
    // Layer 0: 클러스터링
    // ========================

    private Map<String, Object> runLayer0(List<RawNews> rawNews, List<CanonicalNews> existingCanonicals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawNews.size(); i++) {
            RawNews r = rawNews.get(i);
            sb.append(String.format("[%d] %s | %s\n", r.getId(), r.getOriginalTitle(),
                    r.getOriginalDescription() != null ? r.getOriginalDescription().substring(0, Math.min(200, r.getOriginalDescription().length())) : ""));
        }

        StringBuilder canonicalSummary = new StringBuilder();
        for (CanonicalNews cn : existingCanonicals) {
            canonicalSummary.append(String.format("[%d] %s\n", cn.getId(), cn.getTitle()));
        }

        String systemPrompt = """
                너는 한국어로 쓰는 종목 뉴스 클러스터링 분석가다.
                여러 원문 기사를 이슈별로 묶고, 투자 관련성을 판단하고, 기존 canonical 뉴스와 매칭한다.

                규칙:
                1. 같은 이슈의 기사를 하나의 클러스터로 묶어라.
                2. 경제/시장/종목 맥락이 약한 기사는 is_investment_relevant: false로 표시하라.
                3. 기존 canonical news와 같은 이슈면 matched_canonical_id를 지정하라.
                4. 새로운 이슈면 matched_canonical_id: null로 하라.
                5. **큰 이슈를 하나로 합치지 말고 세부 주제별로 분리하라** — 예: "반도체 전반" 하나가 아니라 "삼성 HBM4 수주" / "엔비디아 GTC" / "메모리 가격 전망" 3개로 분리.
                6. 목표: 원문 5~7개당 클러스터 1개 수준.
                7. 개별 기사만 보면 애매하지만 여러 개가 모이면 의미가 분명해지는 경우가 많다 — 클러스터 맥락에서 판단하라.

                JSON 형식으로만 반환하라:
                {
                  "clusters": [
                    {
                      "cluster_id": "c1",
                      "raw_news_ids": [101, 102],
                      "is_investment_relevant": true,
                      "matched_canonical_id": 42 또는 null,
                      "reason": "클러스터링 근거"
                    }
                  ]
                }
                """;

        String userPrompt = "=== 원문 기사 ===\n" + sb + "\n=== 기존 canonical news ===\n" + canonicalSummary;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 8000);
        if (result == null) {
            log.error("Layer 0 failed");
            return null;
        }

        try {
            return objectMapper.convertValue(result, Map.class);
        } catch (Exception e) {
            log.error("Layer 0 result parsing failed", e);
            return null;
        }
    }

    // ========================
    // Layer 1: 분석 (기존 vs 신규)
    // ========================

    private JsonNode runLayer1(CanonicalNews existing, List<String> newRawTexts) {
        String systemPrompt = """
                너는 한국어로 쓰는 종목 뉴스 분석가다.
                기존 canonical 본문을 전문 읽고, 새로 들어온 원문 기사와 정확히 대조하라.
                **읽기와 비교에만 집중하라. 새로 쓰지 마라.**

                분류 기준:
                - new_facts: 기존 본문에 없는 진짜 새로운 사실 (구체적으로 기술)
                - already_covered: 이미 본문에 다루고 있는 내용 (반복 금지 근거)
                - contradictions: 기존 본문과 상충되는 내용 (있으면 기존 정보 수정 근거)
                - obsolete_info: 새 정보로 대체되어 더 이상 유효하지 않은 내용

                **new_facts가 비어있으면 후속 업데이트가 생략된다. 진짜 새로운 사실이 없으면 빈 배열로 반환하라.**

                JSON 형식으로만 반환하라:
                {
                  "new_facts": ["새로운 사실 1", "새로운 사실 2"],
                  "already_covered": ["이미 다루고 있는 내용"],
                  "contradictions": ["기존과 상충되는 내용"],
                  "obsolete_info": ["더 이상 유효하지 않은 정보"]
                }
                """;

        String bodyForPrompt = truncateBody(existing.getBodyMarkdown());
        String truncatedRawTexts = newRawTexts.stream()
                .map(t -> t.length() > 500 ? t.substring(0, 500) + "..." : t)
                .collect(Collectors.joining("\n---\n"));
        String userPrompt = "=== 기존 본문 ===\n" + bodyForPrompt
                + "\n\n=== 새 원문 기사 ===\n" + truncatedRawTexts;

        return cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
    }

    // ========================
    // Layer 2: 생성/커플링 (후속 업데이트)
    // ========================

    private boolean updateCanonical(CanonicalNews existing, ClusterInfo ci, JsonNode analysis, String stockList) {
        String diffSummary = analysis != null ? analysis.toString() : "No analysis result";

        // 시스템 수준 페르소나 지정 (원본 작성자와 다른 페르소나)
        String[] assignedPersona = pickNextPersona();
        // 원본과 같으면 한 번 더 뽑기 (단순 재시도)
        if (assignedPersona[0].equals(existing.getAuthorName())) {
            assignedPersona = pickNextPersona(); // 카운트가 같으면 다음 순서
        }
        String followupAuthorName = assignedPersona[0];
        String followupAuthorEmoji = assignedPersona[1];
        String followupSentiment = assignedPersona[4];
        log.info("후속 업데이트 지정: {} {} (감성: {}, 원본: {})", followupAuthorEmoji, followupAuthorName, followupSentiment, existing.getAuthorName());

        String followupSentimentInstruction = switch (followupSentiment) {
            case "bullish" -> "**후속 업데이트를 호재(bullish) 관점으로 작성하라.** 긍정적 변화, 추가 호재를 부각.";
            case "bearish" -> "**후속 업데이트를 악재(bearish) 관점으로 작성하라.** 새로 드러난 리스크, 우려를 부각.";
            default -> "**후속 업데이트를 중립(neutral) 관점으로 작성하라.** 팩트 중심, 양면 균형.";
        };

        String daysLabel = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));

        String systemPrompt = """
                너는 **%s** %s이다. %s 스타일로 후속 업데이트를 작성한다.
                Layer 1 분석에서 추출된 **new_facts만** 반영하여 후속 업데이트 섹션을 작성하라.
                기존 본문은 수정하지 않는다. 후속 업데이트만 추가한다.

                🎯 **감성 지시**: %s

                규칙:
                - **기존 본문의 내용을 절대 반복하지 않는다.** already_covered로 분류된 내용은 쓰지 마라.
                - new_facts에 해당하는 내용만 간결하게 작성한다.
                - 후속 업데이트 분량은 기존 본문의 1/3~1/2 이내로 유지하라.
                - 핵심 중심으로 압축 — 불필요한 배경 설명 생략.
                - 후속 업데이트에 차트 블록(chart-xxx)은 넣지 마라.
                - 본문에 출처가 명확하지 않은 수치나 통계를 넣지 마라.
                - **sentiment.type은 반드시 "%s"로 설정하라.**

                JSON 형식으로만 반환하라:
                {
                  "followup_title": "후속 업데이트 제목",
                  "followup_markdown": "후속 업데이트 본문(markdown)",
                  "tags": ["태그1", "태그2"],
                  "sentiment": {"type": "%s", "score": 75},
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """.formatted(followupAuthorName, followupAuthorEmoji, assignedPersona[2],
                followupSentimentInstruction, followupSentiment, followupSentiment);

        String bodyForPrompt = truncateBody(existing.getBodyMarkdown());

        String userPrompt = "=== 기존 기사 제목 ===\n" + existing.getTitle()
                + "\n\n=== 기존 본문 (변경하지 말 것) ===\n" + bodyForPrompt
                + "\n\n=== 분석 diff (기존 vs 신규) ===\n" + diffSummary
                + "\n\n=== 원문 (후속 업데이트 근거) ===\n" + ci.rawTexts.stream().map(t -> t.length() > 500 ? t.substring(0, 500) + "..." : t).collect(Collectors.joining("\n---\n"))
                + "\n\n=== 종목 연결 후보 ===\n" + stockList;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 4000);
        if (result == null) {
            log.error("후속 업데이트 생성 실패 (API null): canonicalId={}", existing.getId());
            return false;
        }

        // 시스템 지정 페르소나 강제 사용
        String followupAuthor = followupAuthorName;
        String followupEmoji = followupAuthorEmoji;

        String followupTitle = result.path("followup_title").asText("Follow-up Update");
        String followupBody = result.path("followup_markdown").asText("");
        String followupSection = String.format(
                "\n\n---\n\n**%s 후속 업데이트** | %s\n%s %s\n\n%s",
                daysLabel, followupTitle, followupEmoji, followupAuthor, followupBody);

        existing.setBodyMarkdown(existing.getBodyMarkdown() + followupSection);
        existing.setRawNewsCount(existing.getRawNewsCount() + ci.rawNewsIds.size());
        existing.setPublisherDiversity(calculatePublisherDiversity(ci.rawNewsIds));
        existing.setUpdateCount(existing.getUpdateCount() + 1);

        // tags/sentiment를 후속 업데이트 내용 반영하여 갱신 (기존 것을 덮어씀)
        JsonNode tagsNode = result.path("tags");
        if (tagsNode.isArray()) {
            try { existing.setTagsJson(objectMapper.writeValueAsString(tagsNode)); } catch (Exception ignored) {}
        }

        JsonNode sentimentNode = result.path("sentiment");
        if (!sentimentNode.isMissingNode()) {
            existing.setSentimentType(sentimentNode.path("type").asText(null));
            existing.setSentimentScore(sentimentNode.path("score").isNumber() ? sentimentNode.path("score").asInt() : null);
        }

        // 업데이트 이력 (후속 업데이트 제목+본문 요약을 기록)

        String updateLog = existing.getUpdateLog() != null ? existing.getUpdateLog() : "";
        updateLog += "\n+ " + daysLabel + " 후속: " + followupTitle + " (" + followupEmoji + followupAuthor + ")";
        existing.setUpdateLog(updateLog.trim());

        canonicalNewsRepository.save(existing);
        updateCouplings(existing, result.path("couplings"));
        log.info("후속 업데이트 완료: canonicalId={}, followupTitle={}, author={}", existing.getId(), followupTitle, followupAuthor);
        return true;
    }

    // ========================
    // Layer 2: 생성/커플링 (신규 canonical)
    // ========================

    private boolean createNewCanonical(ClusterInfo ci, String stockList) {

        // 시스템 수준 페르소나+감성 지정 — AI에게 선택권 없음
        String[] assignedPersona = pickNextPersona();
        String personaName = assignedPersona[0];
        String personaEmoji = assignedPersona[1];
        String personaStyle = assignedPersona[2];
        String personaTopics = assignedPersona[3];
        String assignedSentiment = assignedPersona[4];
        log.info("시스템 지정: {} {} (감성: {}, 스타일: {})", personaEmoji, personaName, assignedSentiment, personaStyle);

        String sentimentInstruction = switch (assignedSentiment) {
            case "bullish" -> "**이 뉴스를 호재(bullish) 관점으로 작성하라.** 긍정적 요인, 성장 기대, 수혜 전망을 부각하라.";
            case "bearish" -> "**이 뉴스를 악재(bearish) 관점으로 작성하라.** 리스크 요인, 우려 사항, 하락 가능성을 부각하라.";
            default -> "**이 뉴스를 중립(neutral) 관점으로 작성하라.** 양면을 균형 있게 다루되 팩트 중심으로 서술하라.";
        };

        String systemPrompt = """
                너는 **%s** %s이다. %s 스타일로 뉴스를 작성한다.
                여러 원문 기사를 묶어 하나의 canonical 뉴스를 **%s의 관점과 어조**로 작성하라.
                관련 종목을 연결하고 relevance (1~10)를 부여하라.

                🎯 **감성 지시**: %s

                작성 원칙:
                - 기사 나열이 아니라 **이슈 중심으로 정리**하라.
                - 핵심 중심으로 압축 — 분량은 가능한 짧게.
                - 제목에도 감성이 드러나야 한다 — bullish면 긍정 키워드, bearish면 우려/리스크 키워드.
                - 마크다운 테이블, 볼드, 리스트, 인용문을 적극 활용하여 가독성을 높인다.
                - 본문에 출처가 명확하지 않은 수치나 통계를 넣지 마라.
                - tags는 주제 태그 1~3개. 감성은 tags가 아니라 sentiment 필드로.
                - **sentiment.type은 반드시 "%s"로 설정하라.**
                - **persona 필드에는 반드시 name="%s", emoji="%s"를 그대로 반환하라.**

                차트 블록 규칙:
                - 뉴스당 **최대 1개**만 허용.
                - **모든 뉴스에 차트를 넣지 않는다** — 수치 비교가 명확한 뉴스에만. 전체의 30~50%%%% 정도.
                - 차트에 넣을 데이터가 2개 이하면 차트 대신 텍스트로 서술.
                - 차트 앞뒤에 반드시 **텍스트 문단**.
                - 10가지 차트 타입: bar, line, pie, stacked-bar, waterfall, flow, timeline, gauge, treemap, radar
                - 차트 data는 반드시 label/value 형식 (x/y 금지)

                JSON 형식으로만 반환하라:
                {
                  "title": "뉴스 제목",
                  "markdown": "markdown 본문",
                  "tags": ["태그1", "태그2"],
                  "sentiment": {"type": "%s", "score": 75},
                  "persona": {"name": "%s", "emoji": "%s"},
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """.formatted(personaName, personaEmoji, personaStyle, personaName,
                sentimentInstruction,
                assignedSentiment, personaName, personaEmoji,
                assignedSentiment, personaName, personaEmoji);

        // 각 기사 500자로 truncate하여 프롬프트 크기 제한
        String rawTextsJoined = ci.rawTexts.stream()
                .map(t -> t.length() > 500 ? t.substring(0, 500) + "..." : t)
                .collect(Collectors.joining("\n---\n"));
        String userPrompt = "=== 원문 기사 ===\n" + rawTextsJoined
                + "\n\n=== 종목 연결 후보 ===\n" + stockList;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 4000);
        if (result == null) {
            log.error("Layer 2 신규 생성 실패 (API null): clusterId={}, rawNewsIds={}", ci.clusterId, ci.rawNewsIds);
            return false;
        }

        String tagsJson = null;
        JsonNode tagsNode = result.path("tags");
        if (tagsNode.isArray()) {
            try { tagsJson = objectMapper.writeValueAsString(tagsNode); } catch (Exception ignored) {}
        }

        String sentimentType = null;
        Integer sentimentScore = null;
        JsonNode sentimentNode = result.path("sentiment");
        if (!sentimentNode.isMissingNode()) {
            sentimentType = sentimentNode.path("type").asText(null);
            sentimentScore = sentimentNode.path("score").isNumber() ? sentimentNode.path("score").asInt() : null;
        }

        // 시스템 지정 페르소나 강제 사용 (AI 응답 무시)
        String authorName = personaName;
        String authorEmoji = personaEmoji;

        CanonicalNews canonical = CanonicalNews.builder()
                .title(result.path("title").asText("Untitled"))
                .bodyMarkdown(result.path("markdown").asText(""))
                .rawNewsCount(ci.rawNewsIds.size())
                .publisherDiversity(calculatePublisherDiversity(ci.rawNewsIds))
                .updateCount(0)
                .tagsJson(tagsJson)
                .sentimentType(sentimentType)
                .sentimentScore(sentimentScore)
                .authorName(authorName)
                .authorEmoji(authorEmoji)
                .build();

        canonical = canonicalNewsRepository.save(canonical);

        if (ci.clusterId != null) {
            NewsCluster cluster = newsClusterRepository.findById(ci.clusterId).orElse(null);
            if (cluster != null) {
                cluster.setCanonicalNews(canonical);
                newsClusterRepository.save(cluster);
            }
        }

        updateCouplings(canonical, result.path("couplings"));
        log.info("canonical 신규 생성 완료: id={}, title={}", canonical.getId(), canonical.getTitle());
        return true;
    }

    // ========================
    // 유틸리티
    // ========================

    private List<ClusterInfo> processLayer0Result(Map<String, Object> layer0Result, List<RawNews> rawNews) {
        List<ClusterInfo> clusters = new ArrayList<>();
        Map<Long, RawNews> rawMap = rawNews.stream().collect(Collectors.toMap(RawNews::getId, r -> r));

        List<Map<String, Object>> clusterList = (List<Map<String, Object>>) layer0Result.get("clusters");
        if (clusterList == null) return clusters;

        for (Map<String, Object> c : clusterList) {
            ClusterInfo ci = new ClusterInfo();
            ci.isInvestmentRelevant = Boolean.TRUE.equals(c.get("is_investment_relevant"));

            List<Number> ids = (List<Number>) c.get("raw_news_ids");
            ci.rawNewsIds = ids != null ? ids.stream().map(Number::longValue).toList() : List.of();

            Object matchedId = c.get("matched_canonical_id");
            ci.matchedCanonicalId = matchedId != null && !"null".equals(matchedId.toString())
                    ? Long.parseLong(matchedId.toString()) : null;

            ci.rawTexts = ci.rawNewsIds.stream()
                    .map(rawMap::get)
                    .filter(Objects::nonNull)
                    .map(r -> r.getOriginalTitle() + "\n" + (r.getOriginalDescription() != null ? r.getOriginalDescription() : ""))
                    .toList();

            // 투자 관련 클러스터만 DB에 생성
            if (ci.isInvestmentRelevant && !ci.rawNewsIds.isEmpty()) {
                String reason = (String) c.get("reason");
                if (reason != null && reason.length() > 300) reason = reason.substring(0, 300);
                NewsCluster cluster = NewsCluster.builder()
                        .label(reason)
                        .valid(true)
                        .build();
                cluster = newsClusterRepository.save(cluster);
                ci.clusterId = cluster.getId();

                for (Long rawId : ci.rawNewsIds) {
                    RawNews raw = rawMap.get(rawId);
                    if (raw != null) {
                        raw.setCluster(cluster);
                    }
                }
            }

            clusters.add(ci);
        }

        return clusters;
    }

    private void updateCouplings(CanonicalNews canonical, JsonNode couplingsNode) {
        if (couplingsNode == null || !couplingsNode.isArray()) return;

        for (JsonNode c : couplingsNode) {
            long stockId = c.path("stock_id").asLong(0);
            int relevance = c.path("relevance").asInt(5);
            if (stockId == 0) continue;

            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) continue;

            Optional<StockNewsCoupling> existing = couplingRepository
                    .findByStockIdAndCanonicalNewsId(stockId, canonical.getId());

            if (existing.isPresent()) {
                StockNewsCoupling coupling = existing.get();
                coupling.setRelevance(relevance);
                couplingRepository.save(coupling);
            } else {
                StockNewsCoupling coupling = StockNewsCoupling.builder()
                        .stock(stock)
                        .canonicalNews(canonical)
                        .relevance(relevance)
                        .build();
                couplingRepository.save(coupling);
            }
        }
    }

    /**
     * 본문을 8K 이내로 truncate: 앞 3K + [중략] + 뒤 5K
     * 후속 업데이트가 뒤쪽에 쌓이므로 뒤를 더 많이 유지
     */
    private String truncateBody(String body) {
        if (body == null) return "(없음)";
        if (body.length() <= BODY_LENGTH_CAP) return body;
        int headLen = 3000;
        int tailLen = 5000;
        return body.substring(0, headLen)
                + "\n\n[... 중략 (" + (body.length() - headLen - tailLen) + "자 생략) ...]\n\n"
                + body.substring(body.length() - tailLen);
    }

    /**
     * Layer 1 분석 결과에서 new_facts가 비어있으면 true (= 후속 업데이트 불필요)
     */
    private boolean isAllAlreadyCovered(JsonNode analysis) {
        JsonNode newFacts = analysis.path("new_facts");
        if (!newFacts.isArray() || newFacts.isEmpty()) {
            return true;
        }
        // new_facts가 있지만 전부 빈 문자열이면 커버된 것으로 간주
        for (JsonNode fact : newFacts) {
            if (fact.isTextual() && !fact.asText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private int calculatePublisherDiversity(List<Long> rawNewsIds) {
        return (int) rawNewsIds.stream()
                .map(id -> rawNewsRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(RawNews::getPublisher)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    // ========================
    // Layer 3: 검증
    // ========================

    private void runLayer3AndFix(CanonicalNews updated, ClusterInfo ci, String stockList) {
        String systemPrompt = """
                너는 한국어로 쓰는 종목 뉴스 품질 검증가다.
                업데이트 전후 본문을 비교하여 품질을 검증하라.

                검증 항목:
                1. lost_info: 원문에 있던 중요 정보가 누락되었는가?
                2. unwanted_repetition: 같은 내용이 반복되는가?
                3. coherence_issues: 논리적 비약이나 모순이 있는가?
                4. coupling_validity: 종목 연결이 본문과 일치하는가?
                5. update_log_accuracy: 업데이트 이력이 정확한가?

                JSON 형식으로만 반환하라:
                {
                  "checks": {
                    "lost_info": ["누락된 정보"],
                    "unwanted_repetition": ["반복되는 내용"],
                    "coherence_issues": ["모순/비약 내용"],
                    "coupling_validity": [{"stock_id": 12, "issue": "연결 근거 불충분"}],
                    "update_log_accuracy": true
                  },
                  "passed": true 또는 false,
                  "fix_instructions": "수정 지시 (passed가 false일 때만)"
                }
                """;

        String diffSummary = ci.layer1Analysis != null ? ci.layer1Analysis.toString() : "";
        String userPrompt = "=== 업데이트 전 본문 ===\n" + truncateBody(ci.layer2BeforeMarkdown)
                + "\n\n=== 업데이트 후 본문 ===\n" + truncateBody(updated.getBodyMarkdown())
                + "\n\n=== Layer 1 분석 결과 ===\n" + diffSummary;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 2000);
        if (result == null) {
            log.warn("Layer 3 검증 실패 (API 응답 없음): canonicalId={}", updated.getId());
            return;
        }

        boolean passed = result.path("passed").asBoolean(true);
        log.info("Layer 3 검증: canonicalId={}, passed={}", updated.getId(), passed);

        if (!passed) {
            String fixInstructions = result.path("fix_instructions").asText("");
            log.info("Layer 3 검증 실패 — 수정 지시: {}", fixInstructions);
            retryLayer2WithFix(updated, ci, fixInstructions, stockList);
        }
    }

    private void retryLayer2WithFix(CanonicalNews existing, ClusterInfo ci, String fixInstructions, String stockList) {
        String diffSummary = ci.layer1Analysis != null ? ci.layer1Analysis.toString() : "";

        String systemPrompt = """
                너는 한국어로 쓰는 종목 뉴스 에디터다.
                이전 생성 결과의 검증에서 품질 문제가 발견되었다. 수정 지시를 반영하여 다시 작성하라.

                규칙:
                - 기존 내용을 반복하지 마라.
                - 본문에 출처가 명확하지 않은 업데이트 이력을 포함하라.
                - 수정 지시를 최우선으로 반영하라.

                JSON 형식으로만 반환하라:
                {
                  "title": "수정된 제목",
                  "markdown": "수정된 markdown 본문",
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """;

        String userPrompt = "=== 업데이트 전 본문 ===\n" + truncateBody(ci.layer2BeforeMarkdown)
                + "\n\n=== 분석 diff ===\n" + diffSummary
                + "\n\n=== 원문 ===\n" + ci.rawTexts.stream().map(t -> t.length() > 500 ? t.substring(0, 500) + "..." : t).collect(Collectors.joining("\n---\n"))
                + "\n\n=== 종목 연결 후보 ===\n" + stockList
                + "\n\n=== 수정 지시 ===\n" + fixInstructions;

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 4000);
        if (result == null) return;

        existing.setTitle(result.path("title").asText(existing.getTitle()));
        existing.setBodyMarkdown(result.path("markdown").asText(existing.getBodyMarkdown()));
        canonicalNewsRepository.save(existing);

        updateCouplings(existing, result.path("couplings"));
        log.info("Layer 2 재생성 완료: canonicalId={}", existing.getId());
    }

    // ========================
    // 수동 트리거 (관리자용)
    // ========================

    /**
     * 파이프라인 수동 트리거 (전체)
     */
    @Transactional
    public Map<String, Object> runPipelineManual() {
        long start = System.currentTimeMillis();
        boolean started = runPipeline();
        if (!started) {
            return Map.of(
                    "elapsedMs", 0,
                    "unprocessedRemaining", rawNewsRepository.findByProcessedFalseOrderByPublishedAtDesc().size(),
                    "totalCanonicalNews", canonicalNewsRepository.count(),
                    "skipped", true,
                    "message", "뉴스 파이프라인이 이미 실행 중입니다"
            );
        }
        long elapsed = System.currentTimeMillis() - start;

        int rawCount = rawNewsRepository.findByProcessedFalseOrderByPublishedAtDesc().size();
        int canonicalCount = (int) canonicalNewsRepository.count();

        return Map.of(
                "elapsedMs", elapsed,
                "unprocessedRemaining", rawCount,
                "totalCanonicalNews", canonicalCount,
                "skipped", false,
                "message", "파이프라인 실행 완료 (전체)"
        );
    }

    /**
     * 기존 canonical news의 깨진 인코딩 후속 업데이트 헤더를 정리
     * "????????댁댉??????諛몃마???" → "후속 업데이트"
     */
    @Transactional
    public int fixBrokenFollowupHeaders() {
        List<CanonicalNews> allNews = canonicalNewsRepository.findAll();
        int fixed = 0;
        for (CanonicalNews news : allNews) {
            String body = news.getBodyMarkdown();
            if (body != null && body.contains("????????")) {
                // 깨진 인코딩 패턴을 정상 한국어로 교체
                String cleaned = body.replaceAll("\\*\\*?\\d{4}\\.\\d{2}\\.\\d{2}\\s*\\?{4,}[^*]*\\*\\*?\\s*\\|", "**후속 업데이트** |");
                // 남은 ???? 패턴도 정리 (후속 업데이트 헤더가 아닌 곳)
                cleaned = cleaned.replaceAll("\\?{8,}[가-힣?]*\\?{3,}", "후속 업데이트");
                if (!cleaned.equals(body)) {
                    news.setBodyMarkdown(cleaned);
                    canonicalNewsRepository.save(news);
                    fixed++;
                    log.info("깨진 헤더 수정: canonicalId={}", news.getId());
                }
            }
            // updateLog도 정리
            String updateLog = news.getUpdateLog();
            if (updateLog != null && updateLog.contains("????????")) {
                String cleanedLog = updateLog.replaceAll("\\?{4,}[^\\n]*", "후속");
                if (!cleanedLog.equals(updateLog)) {
                    news.setUpdateLog(cleanedLog);
                    canonicalNewsRepository.save(news);
                }
            }
        }
        // 깨진 페르소나 수정
        Map<String, String[]> personaFixes = Map.of(
                "Macro Desk", new String[]{"매크로봇", "🤖"},
                "Capital Flow", new String[]{"센티멘탈리스트", "🎭"},
                "Battery Watch", new String[]{"배터리언니", "🔋"},
                "Chart Signal", new String[]{"차트장인", "📊"},
                "Value Scout", new String[]{"가치투자왕", "👑"},
                "News Follow-up Desk", new String[]{"뉴스요정", "🧚"},
                "딜러", new String[]{"월가통신", "🗽"},
                "뉴스브리핑", new String[]{"뉴스요정", "🧚"}
        );
        for (CanonicalNews news : allNews) {
            String name = news.getAuthorName();
            String emoji = news.getAuthorEmoji();
            boolean changed = false;

            if (name != null && personaFixes.containsKey(name)) {
                String[] fix = personaFixes.get(name);
                news.setAuthorName(fix[0]);
                news.setAuthorEmoji(fix[1]);
                changed = true;
                log.info("페르소나 수정: canonicalId={}, {} → {}", news.getId(), name, fix[0]);
            }
            // emoji가 ??나 단일 알파벳이면 수정
            if (emoji != null && (emoji.equals("??") || emoji.equals("???") || (emoji.length() == 1 && emoji.charAt(0) < 128))) {
                // 현재 authorName에 맞는 emoji 찾기
                for (String[] p : PERSONA_POOL) {
                    if (p[0].equals(news.getAuthorName())) {
                        news.setAuthorEmoji(p[1]);
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                canonicalNewsRepository.save(news);
                fixed++;
            }
        }

        // 본문에서 "M Macro Desk"를 페르소나 이름으로 교체
        for (CanonicalNews news : canonicalNewsRepository.findAll()) {
            String body = news.getBodyMarkdown();
            if (body != null && body.contains("M Macro Desk")) {
                news.setBodyMarkdown(body.replace("M Macro Desk", "🤖 매크로봇"));
                canonicalNewsRepository.save(news);
                fixed++;
            }
        }

        log.info("깨진 인코딩+페르소나 수정 완료: {}건", fixed);
        return fixed;
    }

    private String buildUpdateCanonicalSystemPrompt(String forbiddenLine) {
        return """
                너는 한국어로 쓰는 종목 뉴스 후속 업데이트 에디터다.
                기존 canonical 뉴스에 새로 들어온 기사 내용을 반영해 후속 업데이트 섹션을 작성한다.

                작성 원칙:
                - 출력은 반드시 한국어 JSON 하나만 반환한다.
                - followup_markdown은 투자자가 바로 이해할 수 있는 자연스러운 한국어로 작성한다.
                - 종목이 급등했다면 "왜 올랐는지"를 짧고 명확하게 설명하는 방향을 우선한다.
                - 제공된 종목 컨텍스트에서 기사와 직접 연결되는 종목만 커플링한다.
                - 변동률이 높다는 이유만으로 종목을 연결하지 않는다.
                - 연결 근거가 약하면 커플링하지 않는다.
                - 차트 블록은 넣지 않는다.
                - persona는 최근 과다 사용된 이름을 피한다.

                persona 제한:
                %s

                JSON 스키마:
                {
                  "followup_title": "후속 업데이트 제목",
                  "followup_markdown": "한국어 markdown 본문",
                  "tags": ["태그1", "태그2"],
                  "sentiment": {"type": "<bullish|bearish|neutral>", "score": 75},
                  "persona": {"name": "작성자명", "emoji": "📰"},
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """.formatted(forbiddenLine);
    }

    private String buildCreateCanonicalSystemPrompt(String recentPersonas, String forbiddenLine) {
        return """
                너는 한국어로 쓰는 종목 뉴스 에디터다.
                여러 원문 기사를 묶어 하나의 canonical 뉴스를 작성한다.

                작성 원칙:
                - 출력은 반드시 한국어 JSON 하나만 반환한다.
                - title과 markdown은 한국어로 작성한다.
                - 투자자가 처음 보는 종목이어도 "왜 오른 것처럼 보이는지"를 짧고 명확하게 설명한다.
                - 제공된 종목 컨텍스트를 참고하되 기사와 직접 연결되는 종목만 커플링한다.
                - 급등 종목이라도 기사상 근거가 약하면 억지로 연결하지 않는다.
                - 연결 근거가 약하면 재료가 제한적이라는 점을 본문에 분명히 적을 수 있다.
                - 차트 블록은 넣지 않는다.
                - 최근 반복된 persona는 피한다.

                최근 persona:
                %s

                persona 제한:
                %s

                JSON 스키마:
                {
                  "title": "기사 제목",
                  "markdown": "한국어 markdown 본문",
                  "tags": ["태그1", "태그2"],
                  "sentiment": {"type": "<bullish|bearish|neutral>", "score": 75},
                  "persona": {"name": "작성자명", "emoji": "📰"},
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """.formatted(recentPersonas, forbiddenLine);
    }

    private String buildRetryCanonicalSystemPrompt() {
        return """
                너는 한국어로 쓰는 종목 뉴스 에디터다.
                기존 결과의 문제점을 반영해 제목, 본문, 커플링을 다시 작성한다.

                작성 원칙:
                - 출력은 반드시 한국어 JSON 하나만 반환한다.
                - fixInstructions를 최우선으로 반영한다.
                - 제목과 본문은 더 직접적이고 간결하게 고친다.
                - 제공된 종목 컨텍스트와 기사 내용에 근거한 종목만 남긴다.
                - 근거가 약한 종목 커플링은 제거한다.

                JSON 스키마:
                {
                  "title": "수정된 제목",
                  "markdown": "수정된 한국어 markdown 본문",
                  "couplings": [{"stock_id": 7, "relevance": 9}]
                }
                """;
    }

    private String buildClusterStockContext(
            List<String> rawTexts,
            List<Stock> rankedCandidates,
            List<Stock> activeStocks,
            String defaultStockList
    ) {
        String joinedText = rawTexts == null ? "" : rawTexts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        if (joinedText.isBlank()) {
            return defaultStockList;
        }

        String lowerText = joinedText.toLowerCase(Locale.ROOT);
        String compactText = compactMatchText(joinedText);

        Map<Long, StockContextEntry> selected = new LinkedHashMap<>();
        addMatchedStocks(selected, rankedCandidates, lowerText, compactText);

        if (selected.size() < CLUSTER_STOCK_CONTEXT_LIMIT) {
            addMatchedStocks(selected, activeStocks, lowerText, compactText);
        }

        List<StockContextEntry> prioritized = selected.values().stream()
                .sorted(this::compareStockContext)
                .limit(CLUSTER_STOCK_CONTEXT_LIMIT)
                .collect(Collectors.toCollection(ArrayList::new));

        if (prioritized.size() < CLUSTER_STOCK_CONTEXT_LIMIT) {
            for (Stock stock : rankedCandidates.stream()
                    .filter(this::isHotStock)
                    .sorted(this::compareStocksByPriority)
                    .toList()) {
                if (selected.containsKey(stock.getId())) {
                    continue;
                }
                prioritized.add(new StockContextEntry(stock, "급등 후보", 3));
                if (prioritized.size() >= CLUSTER_STOCK_CONTEXT_LIMIT
                        || prioritized.size() >= selected.size() + HOT_STOCK_TOP_UP_LIMIT) {
                    break;
                }
            }
        }

        if (prioritized.isEmpty()) {
            return defaultStockList;
        }
        return buildStockContextFromEntries(prioritized, CLUSTER_STOCK_CONTEXT_LIMIT);
    }

    private void addMatchedStocks(
            Map<Long, StockContextEntry> selected,
            List<Stock> stocks,
            String lowerText,
            String compactText
    ) {
        for (Stock stock : stocks) {
            if (stock.getId() == null || selected.containsKey(stock.getId())) {
                continue;
            }

            String matchType = resolveMatchType(stock, lowerText, compactText);
            if (matchType == null) {
                continue;
            }
            selected.put(stock.getId(), new StockContextEntry(stock, matchType, matchPriority(matchType)));
        }
    }

    private String resolveMatchType(Stock stock, String lowerText, String compactText) {
        String stockName = stock.getStockName();
        if (stockName != null) {
            String lowerName = stockName.toLowerCase(Locale.ROOT);
            if (lowerName.length() >= 2 && lowerText.contains(lowerName)) {
                return "기사 본문 매칭";
            }

            String compactName = compactMatchText(stockName);
            if (compactName.length() >= 2 && compactText.contains(compactName)) {
                return "띄어쓰기 무시 매칭";
            }
        }

        String stockCode = stock.getStockCode();
        if (stockCode != null) {
            String lowerCode = stockCode.toLowerCase(Locale.ROOT);
            if (lowerCode.length() >= 4 && lowerText.contains(lowerCode)) {
                return "종목코드 매칭";
            }
        }

        return null;
    }

    private String compactMatchText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_/()\\[\\]{}.,:;\"'`]+", "");
    }

    private String buildStockContext(List<Stock> stocks, String matchType, int limit) {
        List<StockContextEntry> entries = stocks.stream()
                .sorted(this::compareStocksByPriority)
                .limit(limit)
                .map(stock -> new StockContextEntry(stock, matchType, matchPriority(matchType)))
                .toList();
        return buildStockContextFromEntries(entries, limit);
    }

    private String buildStockContextFromEntries(List<StockContextEntry> entries, int limit) {
        if (entries.isEmpty()) {
            return """
                    종목 연결 힌트:
                    - 기사와 직접 연결되는 종목이 확인되지 않았습니다.
                    - 급등만 했다는 이유로 억지 연결하지 마세요.
                    """;
        }

        String lines = entries.stream()
                .limit(limit)
                .map(entry -> formatStockContextLine(entry.stock(), entry.matchType()))
                .collect(Collectors.joining("\n"));

        return """
                종목 연결 힌트:
                - 기사 제목/본문과 직접 연결되는 후보를 먼저 보세요.
                - 급등 종목은 "왜 올랐는지" 설명할 수 있을 때만 연결하세요.
                - 연결 근거가 약하면 억지로 커플링하지 마세요.

                """ + lines;
    }

    private String formatStockContextLine(Stock stock, String matchType) {
        return String.format(
                "[%d] %s (%s/%s, 근거=%s, 변동률=%s, 현재가=%s, 거래량=%s, 시총=%s)",
                stock.getId(),
                stock.getStockName(),
                stock.getStockCode(),
                stock.getMarket(),
                matchType,
                stock.getChangeRate() != null ? stock.getChangeRate().toPlainString() + "%" : "n/a",
                stock.getCurrentPrice() != null ? stock.getCurrentPrice().toPlainString() : "n/a",
                stock.getTradingVolume() != null ? stock.getTradingVolume() : "n/a",
                stock.getMarketCap() != null ? stock.getMarketCap().toPlainString() : "n/a"
        );
    }

    private int compareStockContext(StockContextEntry left, StockContextEntry right) {
        int match = Integer.compare(left.priority(), right.priority());
        if (match != 0) {
            return match;
        }
        return compareStocksByPriority(left.stock(), right.stock());
    }

    private int compareStocksByPriority(Stock left, Stock right) {
        int changeRate = absChangeRate(right).compareTo(absChangeRate(left));
        if (changeRate != 0) {
            return changeRate;
        }

        int volume = Comparator.nullsLast(Long::compareTo).compare(right.getTradingVolume(), left.getTradingVolume());
        if (volume != 0) {
            return volume;
        }

        return Comparator.nullsLast(String::compareTo).compare(left.getStockName(), right.getStockName());
    }

    private BigDecimal absChangeRate(Stock stock) {
        return stock.getChangeRate() == null ? BigDecimal.ZERO : stock.getChangeRate().abs();
    }

    private boolean isHotStock(Stock stock) {
        return stock.getChangeRate() != null && stock.getChangeRate().abs().compareTo(HOT_STOCK_CHANGE_RATE) >= 0;
    }

    private int matchPriority(String matchType) {
        return switch (matchType) {
            case "기사 본문 매칭" -> 0;
            case "띄어쓰기 무시 매칭" -> 1;
            case "종목코드 매칭" -> 2;
            case "급등 후보" -> 3;
            default -> 4;
        };
    }

    /** 클러스터 내부 정보 DTO */
    private static class ClusterInfo {
        Long clusterId;
        List<Long> rawNewsIds = List.of();
        Long matchedCanonicalId;
        boolean isInvestmentRelevant;
        List<String> rawTexts = List.of();
        String layer2BeforeMarkdown;
        JsonNode layer1Analysis;
    }

    private record StockContextEntry(Stock stock, String matchType, int priority) {
    }
}
