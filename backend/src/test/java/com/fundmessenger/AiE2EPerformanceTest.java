package com.fundmessenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * S-45 E2E: 실제 PDF를 사용한 AI 글생성 파이프라인 전체 성능 측정
 *
 * Step 0: PDF → PNG (로컬)
 * Step 1: Gemini Flash — PNG → 텍스트 추출
 * Step 2: Cerebras Qwen3 — 텍스트 → 분석 글 (웹검색 포함)
 *
 * 실행: ./gradlew.bat test --tests "com.fundmessenger.AiE2EPerformanceTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiE2EPerformanceTest {

    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();
    static final Path PDF_DIR = Path.of("../test-ai/pdfs");
    static final Path OUTPUT_DIR = Path.of("../test-ai/outputs");
    static final StringBuilder report = new StringBuilder();

    static String GEMINI_API_KEY;
    static String CEREBRAS_API_KEY;
    static String NAVER_CLIENT_ID;
    static String NAVER_CLIENT_SECRET;
    static boolean geminiAvailable = false;
    static boolean cerebrasAvailable = false;

    // 매체명 매핑 사전 (AiCerebrasService와 동일)
    static final Map<String, String> MEDIA_MAP = Map.ofEntries(
            Map.entry("chosun.com", "조선일보"), Map.entry("hankyung.com", "한국경제"),
            Map.entry("mk.co.kr", "매일경제"), Map.entry("sedaily.com", "서울경제"),
            Map.entry("edaily.co.kr", "이데일리"), Map.entry("etnews.com", "전자신문"),
            Map.entry("yna.co.kr", "연합뉴스"), Map.entry("mt.co.kr", "머니투데이"),
            Map.entry("fnnews.com", "파이낸셜뉴스"), Map.entry("donga.com", "동아일보"),
            Map.entry("joongang.co.kr", "중앙일보"), Map.entry("hani.co.kr", "한겨레"),
            Map.entry("dt.co.kr", "디지털타임스"), Map.entry("newsis.com", "뉴시스"),
            Map.entry("news1.kr", "뉴스1"), Map.entry("newspim.com", "뉴스핌")
    );

    @BeforeAll
    static void setup() throws IOException {
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

        // 키 로드 — 쉼표 구분 시 첫 번째 키 사용
        String geminiKeys = System.getProperty("GEMINI_API_KEYS",
                System.getenv("GEMINI_API_KEYS") != null ? System.getenv("GEMINI_API_KEYS") : "");
        String cerebrasKeys = System.getProperty("CEREBRAS_API_KEYS",
                System.getenv("CEREBRAS_API_KEYS") != null ? System.getenv("CEREBRAS_API_KEYS") : "");

        GEMINI_API_KEY = geminiKeys.contains(",") ? geminiKeys.split(",")[0].trim() : geminiKeys.trim();
        CEREBRAS_API_KEY = cerebrasKeys.contains(",") ? cerebrasKeys.split(",")[0].trim() : cerebrasKeys.trim();
        NAVER_CLIENT_ID = System.getProperty("NAVER_CLIENT_ID",
                System.getenv("NAVER_CLIENT_ID") != null ? System.getenv("NAVER_CLIENT_ID") : "");
        NAVER_CLIENT_SECRET = System.getProperty("NAVER_CLIENT_SECRET",
                System.getenv("NAVER_CLIENT_SECRET") != null ? System.getenv("NAVER_CLIENT_SECRET") : "");

        geminiAvailable = !GEMINI_API_KEY.isBlank();
        cerebrasAvailable = !CEREBRAS_API_KEY.isBlank();

        report.append("# S-45 E2E: 실제 PDF AI 글생성 성능 측정\n\n");
        report.append("실행 시각: ").append(java.time.LocalDateTime.now()).append("\n");
        report.append("Gemini: ").append(geminiAvailable ? "✅" : "❌").append("\n");
        report.append("Cerebras: ").append(cerebrasAvailable ? "✅" : "❌").append("\n\n");
    }

    @AfterAll
    static void writeReport() throws IOException {
        String reportStr = report.toString();
        Files.writeString(OUTPUT_DIR.resolve("s45-e2e-perf-result.md"), reportStr);
        System.out.println("\n" + reportStr);
    }

    // ═══════════════════════════════════════════════
    // E2E: 실제 PDF로 전체 파이프라인 성능 측정
    // ═══════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[E2E] 실제 PDF 1건: 전체 파이프라인 시간 측정")
    void testE2E_singlePdf() throws Exception {
        Assumptions.assumeTrue(geminiAvailable && cerebrasAvailable, "API 키 미설정 — 스킵");

        // 가장 큰 PDF 선택 (삼성전자 ~200KB)
        Path pdfPath = PDF_DIR.resolve("삼성전자_기업분석.pdf");
        Assumptions.assumeTrue(Files.exists(pdfPath), "PDF 파일 없음: " + pdfPath);

        report.append("## 단일 PDF 전체 파이프라인\n\n");
        report.append("대상: `삼성전자_기업분석.pdf` (").append(Files.size(pdfPath) / 1024).append("KB)\n\n");

        long totalStart = System.currentTimeMillis();

        // ── Step 0: PDF → PNG ──
        long step0Start = System.currentTimeMillis();
        List<byte[]> pngPages = pdfToImages(pdfPath, 50);
        long step0Ms = System.currentTimeMillis() - step0Start;
        long totalPngBytes = pngPages.stream().mapToLong(p -> p.length).sum();

        System.out.printf("[Step 0] PDF→PNG: %d페이지, %.1fMB, %dms%n",
                pngPages.size(), totalPngBytes / 1024.0 / 1024.0, step0Ms);

        // ── Step 1: Gemini 텍스트 추출 ──
        long step1Start = System.currentTimeMillis();
        String extractedText = callGemini(pngPages, "삼성전자_기업분석.pdf", "테스트사용자");
        long step1Ms = System.currentTimeMillis() - step1Start;

        System.out.printf("[Step 1] Gemini: %d자 추출, %dms%n",
                extractedText != null ? extractedText.length() : 0, step1Ms);

        Assertions.assertNotNull(extractedText, "Gemini 텍스트 추출 실패");
        Assertions.assertFalse(extractedText.isBlank(), "Gemini 응답이 비어있음");

        // ── Step 2: Cerebras 글 생성 (웹검색 포함) ──
        long step2Start = System.currentTimeMillis();
        String article = callCerebras(extractedText, "테스트사용자");
        long step2Ms = System.currentTimeMillis() - step2Start;

        System.out.printf("[Step 2] Cerebras: %d자 생성, %dms%n",
                article != null ? article.length() : 0, step2Ms);

        long totalMs = System.currentTimeMillis() - totalStart;

        // ── 결과 기록 ──
        report.append("| 단계 | 내용 | 시간 |\n");
        report.append("|------|------|------|\n");
        report.append(String.format("| Step 0 | PDF→PNG (%d페이지, %.1fMB) | **%.1fs** |\n",
                pngPages.size(), totalPngBytes / 1024.0 / 1024.0, step0Ms / 1000.0));
        report.append(String.format("| Step 1 | Gemini 텍스트 추출 (%,d자) | **%.1fs** |\n",
                extractedText.length(), step1Ms / 1000.0));
        report.append(String.format("| Step 2 | Cerebras 글 생성 (%,d자) | **%.1fs** |\n",
                article != null ? article.length() : 0, step2Ms / 1000.0));
        report.append(String.format("| **총합** | **사용자 체감 시간** | **%.1fs** |\n\n",
                totalMs / 1000.0));

        // 추출 텍스트/글 저장
        Files.writeString(OUTPUT_DIR.resolve("s45-extract-삼성전자.md"), extractedText);
        if (article != null) {
            Files.writeString(OUTPUT_DIR.resolve("s45-article-삼성전자.md"), article);
        }
    }

    @Test
    @Order(2)
    @DisplayName("[E2E] PDF 3건 일괄: 파이프라인 시간 비교")
    void testE2E_batchPdf() throws Exception {
        Assumptions.assumeTrue(geminiAvailable && cerebrasAvailable, "API 키 미설정 — 스킵");

        String[] pdfs = {"SK하이닉스_기업분석.pdf", "카카오_기업분석.pdf", "현대자동차_기업분석.pdf"};
        report.append("## PDF 3건 일괄 비교\n\n");
        report.append("| PDF | 크기 | 페이지 | Step0 | Step1 (Gemini) | Step2 (Cerebras) | **총합** |\n");
        report.append("|-----|------|--------|-------|----------------|-----------------|----------|\n");

        for (String pdfName : pdfs) {
            Path pdfPath = PDF_DIR.resolve(pdfName);
            if (!Files.exists(pdfPath)) {
                report.append(String.format("| %s | - | - | - | - | - | 파일 없음 |\n", pdfName));
                continue;
            }

            String label = pdfName.replace("_기업분석.pdf", "");
            long fileSize = Files.size(pdfPath);
            long totalStart = System.currentTimeMillis();

            try {
                // Step 0
                long t0 = System.currentTimeMillis();
                List<byte[]> pages = pdfToImages(pdfPath, 50);
                long step0 = System.currentTimeMillis() - t0;

                // Step 1 — Gemini rate limit 방지: 8초 간격
                Thread.sleep(8000);
                long t1 = System.currentTimeMillis();
                String text = callGemini(pages, pdfName, "테스트사용자");
                long step1 = System.currentTimeMillis() - t1;

                if (text == null || text.isBlank()) {
                    report.append(String.format("| %s | %dKB | %d | %.1fs | ❌ Gemini 실패 | - | - |\n",
                            label, fileSize / 1024, pages.size(), step0 / 1000.0));
                    continue;
                }

                // Step 2
                long t2 = System.currentTimeMillis();
                String article = callCerebras(text, "테스트사용자");
                long step2 = System.currentTimeMillis() - t2;

                long total = System.currentTimeMillis() - totalStart;

                report.append(String.format("| %s | %dKB | %d | %.1fs | %.1fs (%,d자) | %.1fs (%,d자) | **%.1fs** |\n",
                        label, fileSize / 1024, pages.size(),
                        step0 / 1000.0, step1 / 1000.0, text.length(),
                        step2 / 1000.0, article != null ? article.length() : 0,
                        total / 1000.0));

                System.out.printf("[%s] Step0=%.1fs, Step1=%.1fs(%d자), Step2=%.1fs(%d자), 총=%.1fs%n",
                        label, step0 / 1000.0, step1 / 1000.0, text.length(),
                        step2 / 1000.0, article != null ? article.length() : 0, total / 1000.0);

            } catch (Exception e) {
                long total = System.currentTimeMillis() - totalStart;
                report.append(String.format("| %s | %dKB | - | - | ❌ %s | - | %.1fs |\n",
                        label, fileSize / 1024, e.getMessage().substring(0, Math.min(30, e.getMessage().length())),
                        total / 1000.0));
            }
        }
        report.append("\n");
    }

    @Test
    @Order(3)
    @DisplayName("[Gemini 단독] 텍스트 추출만 3회: 응답 시간 분포")
    void testGeminiOnly() throws Exception {
        Assumptions.assumeTrue(geminiAvailable, "Gemini API 키 미설정 — 스킵");

        // 가장 작은 PDF로 Gemini 속도만 3회 측정
        Path pdfPath = PDF_DIR.resolve("현대자동차_기업분석.pdf");
        Assumptions.assumeTrue(Files.exists(pdfPath), "PDF 없음");

        List<byte[]> pages = pdfToImages(pdfPath, 50);

        report.append("## Gemini 단독 응답 시간 (현대자동차, ").append(pages.size()).append("페이지)\n\n");
        report.append("| 회차 | 응답시간 | 추출 글자수 | 입력 토큰 | 출력 토큰 |\n");
        report.append("|------|---------|-----------|----------|----------|\n");

        for (int i = 1; i <= 3; i++) {
            Thread.sleep(8000); // Gemini RPM 방어
            long start = System.currentTimeMillis();
            String text = callGemini(pages, "현대자동차_기업분석.pdf", "테스트사용자");
            long elapsed = System.currentTimeMillis() - start;

            report.append(String.format("| %d | **%.1fs** | %,d | (로그 참조) | (로그 참조) |\n",
                    i, elapsed / 1000.0, text != null ? text.length() : 0));
            System.out.printf("  Gemini #%d: %.1fs, %d자%n", i, elapsed / 1000.0,
                    text != null ? text.length() : 0);
        }
        report.append("\n");
    }

    // ═══════════════════════════════════════════════
    // API 호출 헬퍼
    // ═══════════════════════════════════════════════

    static List<byte[]> pdfToImages(Path pdfPath, int maxPages) throws IOException {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = Math.min(doc.getNumberOfPages(), maxPages);
            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 200);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                images.add(baos.toByteArray());
            }
        }
        return images;
    }

    static String callGemini(List<byte[]> pngPages, String fileName, String authorName) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        for (byte[] img : pngPages) {
            ObjectNode part = parts.addObject();
            ObjectNode inlineData = part.putObject("inline_data");
            inlineData.put("mime_type", "image/png");
            inlineData.put("data", Base64.getEncoder().encodeToString(img));
        }

        String prompt = String.format("""
                분석 대상: %s
                작성자: %s

                이 PDF의 핵심 내용을 구조화된 텍스트로 추출하라.

                ## 추출 규칙
                - 분석 대상 기업명을 첫 줄에 명시하라
                - 모든 수치, 날짜, 고유명사는 원문 그대로 정확히 보존
                - 표/차트 데이터는 마크다운 테이블로 변환
                - 핵심 논지, 투자 포인트, 리스크 요인을 섹션으로 구분
                - PDF에 없는 내용을 추가하지 마라""", fileName, authorName);

        parts.addObject().put("text", prompt);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(res.body());

        if (res.statusCode() != 200) {
            System.err.printf("  Gemini 에러 %d: %s%n", res.statusCode(),
                    res.body().substring(0, Math.min(200, res.body().length())));
            return null;
        }

        String text = result.at("/candidates/0/content/parts/0/text").asText();
        int inputTokens = result.at("/usageMetadata/promptTokenCount").asInt();
        int outputTokens = result.at("/usageMetadata/candidatesTokenCount").asInt();
        System.out.printf("  [Gemini] input=%d, output=%d tokens%n", inputTokens, outputTokens);

        return text;
    }

    static String callCerebras(String extractedText, String uploaderName) throws Exception {
        String systemPrompt = """
                당신은 대학 투자 동아리 게시판에 올릴 기업분석 글을 쓰는 AI다.
                PDF에서 추출한 텍스트를 기반으로, 웹 검색으로 최신 맥락을 보강하여 분석 글을 마크다운으로 작성한다.

                ## 톤
                - "~이다", "~한다" 체. 한국어. 영어 전문용어는 괄호 병기.

                ## 핵심 원칙
                1. PDF의 논조와 관점을 살려라.
                2. 수치는 PDF 그대로, 웹 수치는 매체명 표기.
                3. 출처: [매체: OO] 그대로 사용.

                ## 도구
                - web_search: 3~5회 사용. 핵심 주제별 1회씩.

                ## 금지
                - 인사말, 서명 금지. 결론 반복 금지. 투자 조언 금지.

                ## 출력
                - 마크다운. 첫 줄 ## 제목. H3으로 섹션 구분.

                /no_think""";

        String userMessage = "PDF 추출 텍스트:\n\n---\n" + extractedText + "\n---";

        ObjectNode toolDef = mapper.createObjectNode();
        toolDef.put("type", "function");
        ObjectNode fn = toolDef.putObject("function");
        fn.put("name", "web_search");
        fn.put("description", "웹 검색. 3~5회.");
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties").putObject("query").put("type", "string");
        params.putArray("required").add("query");

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(mapper.createObjectNode().put("role", "user").put("content", userMessage));

        int totalInput = 0, totalOutput = 0, round = 0;

        while (round < 10) {
            round++;
            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", "qwen-3-235b-a22b-instruct-2507");
            ArrayNode msgArray = reqBody.putArray("messages");
            for (ObjectNode msg : messages) msgArray.add(msg);
            reqBody.putArray("tools").add(toolDef);
            reqBody.put("max_tokens", 8000);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cerebras.ai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + CEREBRAS_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 429) {
                System.out.println("  [Cerebras] 429 — 10초 대기");
                Thread.sleep(10000);
                round--;
                continue;
            }

            JsonNode result = mapper.readTree(res.body());
            JsonNode choice = result.at("/choices/0");
            totalInput += result.at("/usage/prompt_tokens").asInt(0);
            totalOutput += result.at("/usage/completion_tokens").asInt(0);

            JsonNode toolCalls = choice.at("/message/tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                ObjectNode assistantMsg = mapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("tool_calls", toolCalls);
                String contentText = choice.at("/message/content").asText(null);
                if (contentText != null) assistantMsg.put("content", contentText);
                messages.add(assistantMsg);

                for (JsonNode tc : toolCalls) {
                    String args = tc.at("/function/arguments").asText();
                    JsonNode argsNode = mapper.readTree(args);
                    String query = argsNode.get("query").asText();
                    System.out.printf("  [검색 R%d] %s%n", round, query);
                    String searchResult = webSearch(query);
                    ObjectNode toolMsg = mapper.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.get("id").asText());
                    toolMsg.put("content", searchResult);
                    messages.add(toolMsg);
                }
                continue;
            }

            String output = choice.at("/message/content").asText();
            System.out.printf("  [Cerebras] %d라운드, 검색%d회, 토큰: %d→%d%n",
                    round, round - 1, totalInput, totalOutput);
            return output;
        }
        return null;
    }

    static String webSearch(String query) {
        if (NAVER_CLIENT_ID.isBlank()) return "검색 API 미설정";
        try {
            String url = "https://openapi.naver.com/v1/search/news.json?query="
                    + java.net.URLEncoder.encode(query, "UTF-8") + "&display=5&sort=date";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Naver-Client-Id", NAVER_CLIENT_ID)
                    .header("X-Naver-Client-Secret", NAVER_CLIENT_SECRET)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode items = mapper.readTree(res.body()).path("items");
            if (items.isEmpty()) return "검색 결과 없음";

            StringBuilder sb = new StringBuilder();
            for (JsonNode item : items) {
                String title = item.path("title").asText().replaceAll("<[^>]*>", "");
                String desc = item.path("description").asText().replaceAll("<[^>]*>", "");
                String link = item.path("originallink").asText();
                if (link.isBlank()) link = item.path("link").asText();
                String media = getMediaName(link);
                sb.append("- [매체: ").append(media).append("] ").append(title)
                        .append(" — ").append(desc).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "검색 오류: " + e.getMessage();
        }
    }

    static String getMediaName(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return "언론보도";
            host = host.replaceFirst("^www\\.", "");
            if (MEDIA_MAP.containsKey(host)) return MEDIA_MAP.get(host);
            String[] parts = host.split("\\.");
            if (parts.length > 2) {
                String base = parts[parts.length - 2] + "." + parts[parts.length - 1];
                if (MEDIA_MAP.containsKey(base)) return MEDIA_MAP.get(base);
            }
            return "언론보도";
        } catch (Exception e) {
            return "언론보도";
        }
    }
}
