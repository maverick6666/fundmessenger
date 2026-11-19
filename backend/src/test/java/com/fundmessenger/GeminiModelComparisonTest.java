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
 * Gemini 모델 4종 비교: 동일 PDF로 텍스트 추출 품질 + 속도 비교
 *
 * 대상 모델:
 * 1. gemini-2.5-flash        (현재 사용 중)
 * 2. gemini-2.5-flash-lite   (15 RPM, 1000 RPD — 최고 처리량)
 * 3. gemini-2.5-pro          (5 RPM, 100 RPD — 최고 추론)
 * 4. gemini-3-flash-preview  (10 RPM, 500 RPD — 문서 추출 +15%)
 *
 * 실행: ./gradlew.bat test --tests "com.fundmessenger.GeminiModelComparisonTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeminiModelComparisonTest {

    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();
    static final Path PDF_DIR = Path.of("../test-ai/pdfs");
    static final Path OUTPUT_DIR = Path.of("../test-ai/outputs");
    static final StringBuilder report = new StringBuilder();

    static String GEMINI_API_KEY;
    static List<byte[]> pngPages;
    static String pdfName = "삼성전자_기업분석.pdf";
    static int pageCount;

    static final String[] MODELS = {
            "gemini-2.5-flash",          // 현재 사용 중
            "gemini-2.5-flash-lite",     // 경량 고속
            "gemini-2.5-pro",            // 최고 추론
            "gemini-3-flash-preview"     // 문서 추출 특화
    };

    static final String[] MODEL_LABELS = {
            "2.5 Flash (현재)",
            "2.5 Flash-Lite",
            "2.5 Pro",
            "3 Flash Preview"
    };

    static final String PROMPT = """
            분석 대상: 삼성전자_기업분석.pdf
            작성자: 테스트사용자

            이 PDF의 핵심 내용을 구조화된 텍스트로 추출하라.

            ## 추출 규칙
            - 분석 대상 기업명을 첫 줄에 명시하라
            - 모든 수치, 날짜, 고유명사는 원문 그대로 정확히 보존 (단위 포함)
            - 표/차트 데이터는 마크다운 테이블로 변환
            - 핵심 논지, 투자 포인트, 리스크 요인을 섹션으로 구분
            - 불필요한 서식(페이지 번호, 헤더/푸터, 면책조항)은 제거
            - PDF에 없는 내용을 추가하지 마라""";

    @BeforeAll
    static void setup() throws Exception {
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

        String geminiKeys = System.getProperty("GEMINI_API_KEYS",
                System.getenv("GEMINI_API_KEYS") != null ? System.getenv("GEMINI_API_KEYS") : "");
        GEMINI_API_KEY = geminiKeys.contains(",") ? geminiKeys.split(",")[0].trim() : geminiKeys.trim();

        Assumptions.assumeTrue(!GEMINI_API_KEY.isBlank(), "GEMINI_API_KEYS 미설정");

        // PDF → PNG 한 번만 변환 (모든 모델이 동일 입력 사용)
        Path pdfPath = PDF_DIR.resolve(pdfName);
        Assumptions.assumeTrue(Files.exists(pdfPath), "PDF 없음: " + pdfPath);

        System.out.println("PDF→PNG 변환 시작: " + pdfName);
        long t = System.currentTimeMillis();
        pngPages = pdfToImages(pdfPath, 50);
        pageCount = pngPages.size();
        long pngBytes = pngPages.stream().mapToLong(p -> p.length).sum();
        System.out.printf("PDF→PNG 완료: %d페이지, %.1fMB, %dms%n",
                pageCount, pngBytes / 1024.0 / 1024.0, System.currentTimeMillis() - t);

        report.append("# Gemini 모델 4종 비교 테스트\n\n");
        report.append("실행 시각: ").append(java.time.LocalDateTime.now()).append("\n");
        report.append("대상 PDF: `").append(pdfName).append("` (").append(pageCount).append("페이지, ");
        report.append(String.format("%.1fMB PNG)\n\n", pngBytes / 1024.0 / 1024.0));
    }

    @AfterAll
    static void writeReport() throws IOException {
        String reportStr = report.toString();
        Files.writeString(OUTPUT_DIR.resolve("gemini-model-comparison.md"), reportStr);
        System.out.println("\n" + reportStr);
    }

    @Test
    @Order(1)
    @DisplayName("모델 1: gemini-2.5-flash (현재 사용 중)")
    void test1_flash25() throws Exception {
        runModelTest(0);
    }

    @Test
    @Order(2)
    @DisplayName("모델 2: gemini-2.5-flash-lite")
    void test2_flashLite() throws Exception {
        Thread.sleep(10000); // RPM 방어
        runModelTest(1);
    }

    @Test
    @Order(3)
    @DisplayName("모델 3: gemini-2.5-pro")
    void test3_pro25() throws Exception {
        Thread.sleep(10000);
        runModelTest(2);
    }

    @Test
    @Order(4)
    @DisplayName("모델 4: gemini-3-flash-preview")
    void test4_flash3() throws Exception {
        Thread.sleep(10000);
        runModelTest(3);
    }

    @Test
    @Order(5)
    @DisplayName("최종 비교표")
    void test5_summary() throws IOException {
        // 저장된 파일들 읽어서 글자 수 비교
        report.append("\n## 최종 비교 요약\n\n");
        report.append("| 항목 | 2.5 Flash | 2.5 Flash-Lite | 2.5 Pro | 3 Flash Preview |\n");
        report.append("|------|-----------|----------------|---------|------------------|\n");
        report.append("| 무료 RPM | 10 | **15** | 5 | 10 |\n");
        report.append("| 무료 RPD | 500 | **1,000** | 100 | 500 |\n");
        report.append("| OCR Arena ELO | 1642 (#4) | — | — | **1762 (#1)** |\n");
        report.append("| 문서 추출 정확도 | 기준 | 기준 이하 | 기준 이상 | **+15%** |\n");
        report.append("| 추천 용도 | 범용 | 고처리량 | 복잡 추론 | **문서 추출 특화** |\n");
        report.append("\n");

        // 출력 파일 크기 비교
        report.append("### 출력 길이 비교\n\n");
        for (int i = 0; i < MODELS.length; i++) {
            Path outFile = OUTPUT_DIR.resolve("gemini-compare-" + MODELS[i] + ".md");
            if (Files.exists(outFile)) {
                String content = Files.readString(outFile);
                int lines = content.split("\n").length;
                int tables = countTables(content);
                report.append(String.format("- **%s**: %,d자, %d줄, 표 %d개\n",
                        MODEL_LABELS[i], content.length(), lines, tables));
            }
        }
        report.append("\n> 각 모델의 전체 출력은 `test-ai/outputs/gemini-compare-{모델명}.md` 참조\n");
    }

    // ═══════════════════════════════════════════════
    // 모델 테스트 실행
    // ═══════════════════════════════════════════════

    private void runModelTest(int modelIdx) throws Exception {
        String model = MODELS[modelIdx];
        String label = MODEL_LABELS[modelIdx];

        System.out.printf("%n=== [%s] %s ===%n", model, label);
        report.append("## ").append(label).append(" (`").append(model).append("`)\n\n");

        long start = System.currentTimeMillis();
        CallResult result = callGemini(model, pngPages, PROMPT);
        long elapsed = System.currentTimeMillis() - start;

        if (result.error != null) {
            System.out.printf("  ❌ 실패: %s%n", result.error);
            report.append(String.format("**❌ 실패**: %s (%.1fs)\n\n", result.error, elapsed / 1000.0));
            return;
        }

        String text = result.text;
        int lines = text.split("\n").length;
        int tables = countTables(text);

        System.out.printf("  ✅ 완료: %,d자, %d줄, 표 %d개, %dms%n", text.length(), lines, tables, elapsed);
        System.out.printf("  토큰: input=%d, output=%d%n", result.inputTokens, result.outputTokens);
        System.out.printf("  미리보기: %s%n", text.substring(0, Math.min(150, text.length())).replace("\n", " "));

        report.append(String.format("| 항목 | 값 |\n|------|----|\n"));
        report.append(String.format("| 응답 시간 | **%.1fs** |\n", elapsed / 1000.0));
        report.append(String.format("| 추출 글자 수 | %,d자 |\n", text.length()));
        report.append(String.format("| 줄 수 | %d |\n", lines));
        report.append(String.format("| 표 개수 | %d |\n", tables));
        report.append(String.format("| 입력 토큰 | %,d |\n", result.inputTokens));
        report.append(String.format("| 출력 토큰 | %,d |\n", result.outputTokens));
        report.append("\n**미리보기** (첫 300자):\n```\n");
        report.append(text.substring(0, Math.min(300, text.length())));
        report.append("\n```\n\n");

        // 전체 출력 저장
        Files.writeString(OUTPUT_DIR.resolve("gemini-compare-" + model + ".md"), text);
    }

    // ═══════════════════════════════════════════════
    // API 호출
    // ═══════════════════════════════════════════════

    record CallResult(String text, int inputTokens, int outputTokens, String error) {}

    static CallResult callGemini(String model, List<byte[]> pngPages, String prompt) {
        try {
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
            parts.addObject().put("text", prompt);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + GEMINI_API_KEY;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                String errBody = res.body().substring(0, Math.min(300, res.body().length()));
                return new CallResult(null, 0, 0, "HTTP " + res.statusCode() + ": " + errBody);
            }

            JsonNode root = mapper.readTree(res.body());

            // 안전 체크
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty() || candidates.get(0) == null) {
                return new CallResult(null, 0, 0, "candidates 비어있음");
            }

            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            int inputTokens = root.path("usageMetadata").path("promptTokenCount").asInt();
            int outputTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt();

            return new CallResult(text, inputTokens, outputTokens, null);
        } catch (Exception e) {
            return new CallResult(null, 0, 0, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════
    // 유틸
    // ═══════════════════════════════════════════════

    static List<byte[]> pdfToImages(Path pdfPath, int maxPages) throws IOException {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int count = Math.min(doc.getNumberOfPages(), maxPages);
            for (int i = 0; i < count; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 200);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                images.add(baos.toByteArray());
                img.flush();
            }
        }
        return images;
    }

    static int countTables(String text) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("|") && line.trim().endsWith("|") && line.contains("---")) {
                count++;
            }
        }
        return count;
    }
}
