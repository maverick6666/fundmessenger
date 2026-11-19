package com.fundmessenger.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDataService {

    private final HypeScoreLogRepository hypeScoreLogRepository;
    private final AttentionLogRepository attentionLogRepository;
    private final NewsEngagementRepository newsEngagementRepository;
    private final ScoreDailySnapshotRepository scoreDailySnapshotRepository;
    private final UserRepository userRepository;

    /** exportId -> { bytes, period, createdAt } for pending deletes */
    private final ConcurrentHashMap<String, ExportRecord> pendingExports = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ========== Stats ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(Long adminUserId) {
        validateSuperAdmin(adminUserId);

        LocalDateTime minScore = hypeScoreLogRepository.findMinCalculatedAt();
        LocalDateTime maxScore = hypeScoreLogRepository.findMaxCalculatedAt();
        LocalDateTime minAttention = attentionLogRepository.findMinCalculatedAt();
        LocalDateTime maxAttention = attentionLogRepository.findMaxCalculatedAt();
        LocalDateTime minEngagement = newsEngagementRepository.findMinCreatedAt();
        LocalDateTime maxEngagement = newsEngagementRepository.findMaxCreatedAt();
        LocalDate minSnapshot = scoreDailySnapshotRepository.findMinSnapshotDate();
        LocalDate maxSnapshot = scoreDailySnapshotRepository.findMaxSnapshotDate();

        // overall period
        LocalDate earliest = earliest(
                minScore != null ? minScore.toLocalDate() : null,
                minAttention != null ? minAttention.toLocalDate() : null,
                minEngagement != null ? minEngagement.toLocalDate() : null,
                minSnapshot
        );
        LocalDate latest = latest(
                maxScore != null ? maxScore.toLocalDate() : null,
                maxAttention != null ? maxAttention.toLocalDate() : null,
                maxEngagement != null ? maxEngagement.toLocalDate() : null,
                maxSnapshot
        );

        long scoreCount = hypeScoreLogRepository.count();
        long attentionCount = attentionLogRepository.count();
        long engagementCount = newsEngagementRepository.count();
        long snapshotCount = scoreDailySnapshotRepository.count();

        long days = (earliest != null && latest != null) ? ChronoUnit.DAYS.between(earliest, latest) + 1 : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("periodFrom", earliest);
        stats.put("periodTo", latest);
        stats.put("days", days);
        stats.put("scoreLogCount", scoreCount);
        stats.put("attentionLogCount", attentionCount);
        stats.put("engagementCount", engagementCount);
        stats.put("snapshotCount", snapshotCount);
        // rough size estimates (bytes per row)
        stats.put("estimatedSizeMb", Math.round(
                (scoreCount * 200L + attentionCount * 120L + engagementCount * 120L + snapshotCount * 100L) / (1024.0 * 1024.0) * 10
        ) / 10.0);

        return stats;
    }

    // ========== Export ==========

    @Transactional(readOnly = true)
    public ExportResult export(LocalDate from, LocalDate to, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        List<HypeScoreLog> scoreLogs = hypeScoreLogRepository.findByCalculatedAtBetween(fromDt, toDt);
        List<AttentionLog> attentionLogs = attentionLogRepository.findByCalculatedAtBetween(fromDt, toDt);
        List<NewsEngagement> engagements = newsEngagementRepository.findByCreatedAtBetween(fromDt, toDt);
        List<ScoreDailySnapshot> snapshots = scoreDailySnapshotRepository.findBySnapshotDateBetween(from, to);

        try {
            byte[] zipBytes = buildZip(scoreLogs, attentionLogs, engagements, snapshots, from, to);

            String exportId = UUID.randomUUID().toString();
            pendingExports.put(exportId, new ExportRecord(from, to, LocalDateTime.now()));

            // Schedule cleanup after 24h
            scheduleCleanup(exportId);

            log.info("모델 데이터 내보내기: exportId={}, period={} ~ {}, score={}, attention={}, engagement={}, snapshot={}, zipSize={}KB",
                    exportId, from, to, scoreLogs.size(), attentionLogs.size(), engagements.size(), snapshots.size(),
                    zipBytes.length / 1024);

            return new ExportResult(exportId, zipBytes,
                    "hype-model-export-" + LocalDate.now() + ".zip");
        } catch (IOException e) {
            throw new BadRequestException("ZIP 생성 실패: " + e.getMessage());
        }
    }

    // ========== Delete after confirmed download ==========

    @Transactional
    public Map<String, Object> deleteExportedData(String exportId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        ExportRecord record = pendingExports.remove(exportId);
        if (record == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 내보내기 ID입니다: " + exportId);
        }

        LocalDateTime fromDt = record.from.atStartOfDay();
        LocalDateTime toDt = record.to.plusDays(1).atStartOfDay();

        int deletedScores = hypeScoreLogRepository.deleteByCalculatedAtBetween(fromDt, toDt);
        int deletedAttention = attentionLogRepository.deleteByCalculatedAtBetween(fromDt, toDt);
        int deletedEngagement = newsEngagementRepository.deleteByCreatedAtBetween(fromDt, toDt);
        int deletedSnapshots = scoreDailySnapshotRepository.deleteBySnapshotDateBetween(record.from, record.to);

        log.info("모델 데이터 삭제: exportId={}, period={} ~ {}, scores={}, attention={}, engagement={}, snapshots={}",
                exportId, record.from, record.to, deletedScores, deletedAttention, deletedEngagement, deletedSnapshots);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportId", exportId);
        result.put("periodFrom", record.from);
        result.put("periodTo", record.to);
        result.put("deletedScoreLogs", deletedScores);
        result.put("deletedAttentionLogs", deletedAttention);
        result.put("deletedEngagements", deletedEngagement);
        result.put("deletedSnapshots", deletedSnapshots);
        return result;
    }

    // ========== ZIP builder ==========

    private byte[] buildZip(
            List<HypeScoreLog> scoreLogs,
            List<AttentionLog> attentionLogs,
            List<NewsEngagement> engagements,
            List<ScoreDailySnapshot> snapshots,
            LocalDate from, LocalDate to
    ) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // manifest.json
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("exported_at", LocalDateTime.now().toString());
            manifest.put("period", Map.of("from", from.toString(), "to", to.toString()));
            manifest.put("row_counts", Map.of(
                    "score_logs", scoreLogs.size(),
                    "attention_logs", attentionLogs.size(),
                    "engagements", engagements.size(),
                    "daily_snapshots", snapshots.size()
            ));
            writeZipEntry(zos, "manifest.json", MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));

            // score_logs.jsonl
            writeJsonlEntry(zos, "score_logs.jsonl", scoreLogs, this::scoreLogToMap);

            // attention_logs.jsonl
            writeJsonlEntry(zos, "attention_logs.jsonl", attentionLogs, this::attentionLogToMap);

            // engagement_logs.jsonl
            writeJsonlEntry(zos, "engagement_logs.jsonl", engagements, this::engagementToMap);

            // daily_snapshots.csv
            writeCsvEntry(zos, "daily_snapshots.csv", snapshots);
        }
        return baos.toByteArray();
    }

    private <T> void writeJsonlEntry(ZipOutputStream zos, String name, List<T> items, java.util.function.Function<T, Map<String, Object>> mapper) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        OutputStreamWriter writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
        for (T item : items) {
            writer.write(MAPPER.writeValueAsString(mapper.apply(item)));
            writer.write('\n');
        }
        writer.flush();
        zos.closeEntry();
    }

    private void writeZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private void writeCsvEntry(ZipOutputStream zos, String name, List<ScoreDailySnapshot> snapshots) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        OutputStreamWriter writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
        writer.write("snapshot_date,stock_id,news_count,avg_hype,max_hype,min_hype,attention,bucket_0_20,bucket_20_40,bucket_40_60,bucket_60_80,bucket_80_100\n");
        for (ScoreDailySnapshot s : snapshots) {
            writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    s.getSnapshotDate(),
                    s.getStock() != null ? s.getStock().getId() : "",
                    nvl(s.getNewsCount()),
                    nvl(s.getAvgHype()),
                    nvl(s.getMaxHype()),
                    nvl(s.getMinHype()),
                    nvl(s.getAttention()),
                    nvl(s.getBucket0_20()),
                    nvl(s.getBucket20_40()),
                    nvl(s.getBucket40_60()),
                    nvl(s.getBucket60_80()),
                    nvl(s.getBucket80_100())
            ));
        }
        writer.flush();
        zos.closeEntry();
    }

    // ========== Row mappers ==========

    private Map<String, Object> scoreLogToMap(HypeScoreLog h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calculated_at", h.getCalculatedAt());
        m.put("coupling_id", h.getCoupling() != null ? h.getCoupling().getId() : null);
        m.put("news_id", h.getCanonicalNews() != null ? h.getCanonicalNews().getId() : null);
        m.put("stock_id", h.getStock() != null ? h.getStock().getId() : null);
        m.put("formula_version", h.getFormulaVersion());
        m.put("prompt_version", h.getPromptVersion());
        m.put("inputs", Map.of(
                "relevance", nvl(h.getRelevance()),
                "raw_count", nvl(h.getRawCount()),
                "publisher_count", nvl(h.getPublisherCount()),
                "days_since_update", nvl(h.getDaysSinceUpdate()),
                "comments_7d", nvl(h.getRecentCommentCount7d())
        ));
        m.put("calc", Map.of(
                "strength", nvl(h.getStrength()),
                "freshness", nvl(h.getFreshness()),
                "reaction", nvl(h.getReaction()),
                "instant_hype", nvl(h.getInstantHype())
        ));
        m.put("ema", Map.of(
                "previous", nvl(h.getPreviousHype()),
                "alpha", nvl(h.getAlphaUsed()),
                "hours_elapsed", nvl(h.getHoursElapsed()),
                "adaptive", nvl(h.getAdaptiveTriggered()),
                "stored", nvl(h.getStoredHype())
        ));
        return m;
    }

    private Map<String, Object> attentionLogToMap(AttentionLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calculated_at", a.getCalculatedAt());
        m.put("stock_id", a.getStock() != null ? a.getStock().getId() : null);
        m.put("formula_version", a.getFormulaVersion());
        m.put("top_hype_1", a.getTopHype1());
        m.put("top_hype_2", a.getTopHype2());
        m.put("top_hype_3", a.getTopHype3());
        m.put("avg_hype", a.getAvgHype());
        m.put("recent_thread_count_7d", a.getRecentThreadCount7d());
        m.put("recent_comment_count_7d", a.getRecentCommentCount7d());
        m.put("norm_thread", a.getNormThread());
        m.put("norm_comment", a.getNormComment());
        m.put("attention", a.getAttention());
        return m;
    }

    private Map<String, Object> engagementToMap(NewsEngagement e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created_at", e.getCreatedAt());
        m.put("user_hash", e.getUserHash());
        m.put("coupling_id", e.getCoupling() != null ? e.getCoupling().getId() : null);
        m.put("news_id", e.getCanonicalNews() != null ? e.getCanonicalNews().getId() : null);
        m.put("stock_id", e.getStock() != null ? e.getStock().getId() : null);
        m.put("action", e.getAction());
        m.put("position_in_list", e.getPositionInList());
        m.put("hype_at_time", e.getHypeAtTime());
        m.put("attention_at_time", e.getAttentionAtTime());
        m.put("dwell_seconds", e.getDwellSeconds());
        return m;
    }

    // ========== Helpers ==========

    private static Object nvl(Object v) { return v != null ? v : ""; }

    private LocalDate earliest(LocalDate... dates) {
        return Arrays.stream(dates).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
    }

    private LocalDate latest(LocalDate... dates) {
        return Arrays.stream(dates).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private void validateSuperAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
        if (!user.isSuperAdmin()) {
            throw new SecurityException("슈퍼어드민 권한이 필요합니다");
        }
    }

    private void scheduleCleanup(String exportId) {
        // Simple timer-based cleanup after 24 hours
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                ExportRecord removed = pendingExports.remove(exportId);
                if (removed != null) {
                    log.info("미확인 내보내기 만료 삭제: exportId={}", exportId);
                }
            }
        }, 24 * 60 * 60 * 1000L);
    }

    // ========== Records ==========

    public record ExportRecord(LocalDate from, LocalDate to, LocalDateTime createdAt) {}
    public record ExportResult(String exportId, byte[] zipBytes, String filename) {}
}
