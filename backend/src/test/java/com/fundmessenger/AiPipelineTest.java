package com.fundmessenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

/**
 * AI 글생성 파이프라인 통합 테스트 (프롬프트 v4)
 *
 * P1 해결: 메타데이터(파일명, 업로더명) 주입
 * P2 해결: PDF → PNG 이미지 변환 후 Gemini 전송 (폰트 인코딩 우회 아닌 근본 해결)
 * P3 해결: 수치 직접 계산 금지 규칙
 * P4 해결: 매체명 매핑 사전 확장
 */
class AiPipelineTest {

    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();

    static String GEMINI_API_KEY;
    static String CEREBRAS_API_KEY;
    static String NAVER_CLIENT_ID;
    static String NAVER_CLIENT_SECRET;

    static final Path PDF_DIR = Path.of("../test-ai/pdfs");
    static final Path OUTPUT_DIR = Path.of("../test-ai/outputs");

    // ── 매체명 매핑 사전 (P4) ──
    static final Map<String, String> MEDIA_MAP = Map.ofEntries(
            Map.entry("chosun.com", "조선일보"), Map.entry("biz.chosun.com", "조선비즈"),
            Map.entry("hankyung.com", "한국경제"), Map.entry("mk.co.kr", "매일경제"),
            Map.entry("sedaily.com", "서울경제"), Map.entry("edaily.co.kr", "이데일리"),
            Map.entry("asiatoday.co.kr", "아시아투데이"), Map.entry("etnews.com", "전자신문"),
            Map.entry("zdnet.co.kr", "ZDNet Korea"), Map.entry("newsis.com", "뉴시스"),
            Map.entry("yna.co.kr", "연합뉴스"), Map.entry("yonhapnews.co.kr", "연합뉴스"),
            Map.entry("mt.co.kr", "머니투데이"), Map.entry("fnnews.com", "파이낸셜뉴스"),
            Map.entry("heraldcorp.com", "헤럴드경제"), Map.entry("donga.com", "동아일보"),
            Map.entry("joongang.co.kr", "중앙일보"), Map.entry("hani.co.kr", "한겨레"),
            Map.entry("khan.co.kr", "경향신문"), Map.entry("dt.co.kr", "디지털타임스"),
            Map.entry("bloter.net", "블로터"), Map.entry("theguru.co.kr", "더구루"),
            Map.entry("businesspost.co.kr", "비즈니스포스트"), Map.entry("ilovepc.co.kr", "디지털포스트"),
            Map.entry("infomax.co.kr", "연합인포맥스"), Map.entry("news1.kr", "뉴스1"),
            Map.entry("newspim.com", "뉴스핌"), Map.entry("thebell.co.kr", "더벨"),
            Map.entry("wowtv.co.kr", "한국경제TV"), Map.entry("sbs.co.kr", "SBS뉴스"),
            Map.entry("ytn.co.kr", "YTN"), Map.entry("economist.co.kr", "이코노미스트"),
            Map.entry("investchosun.com", "인베스트조선"),
            // v4 추가
            Map.entry("g-enews.com", "글로벌이코노믹"), Map.entry("ceoscoredaily.com", "CEO스코어데일리"),
            Map.entry("hellot.net", "헬로티"), Map.entry("inews24.com", "아이뉴스24"),
            Map.entry("byline.network", "바이라인네트워크"), Map.entry("econovill.com", "이코노믹리뷰"),
            Map.entry("insightkorea.co.kr", "인사이트코리아"), Map.entry("press9.kr", "프레스나인"),
            Map.entry("cbci.co.kr", "CBCI"), Map.entry("gokorea.kr", "고코리아"),
            Map.entry("joseilbo.com", "조세일보"), Map.entry("newdaily.co.kr", "뉴데일리"),
            Map.entry("digitaltoday.co.kr", "디지털투데이"), Map.entry("thelec.kr", "더일렉"),
            Map.entry("ddaily.co.kr", "디지털데일리"), Map.entry("theelec.kr", "디일렉"),
            Map.entry("joins.com", "중앙일보"), Map.entry("koreajoongangdaily.joins.com", "코리아중앙데일리")
    );

    @BeforeAll
    static void loadEnv() throws IOException {
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
        GEMINI_API_KEY = System.getProperty("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
        CEREBRAS_API_KEY = System.getProperty("CEREBRAS_API_KEY", System.getenv("CEREBRAS_API_KEY"));
        NAVER_CLIENT_ID = System.getProperty("NAVER_CLIENT_ID", System.getenv("NAVER_CLIENT_ID"));
        NAVER_CLIENT_SECRET = System.getProperty("NAVER_CLIENT_SECRET", System.getenv("NAVER_CLIENT_SECRET"));

        Files.createDirectories(OUTPUT_DIR);
    }

    // ══════════════════════════════════════════
    // Step 1: PDF → PNG → Gemini (P2 근본 해결)
    // ══════════════════════════════════════════

    /** PDF 페이지를 200 DPI PNG 이미지로 변환 */
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

    /** Gemini에 이미지들 + 프롬프트 전송 (P1: 메타데이터 주입) */
    static String extractWithGemini(List<byte[]> images, String fileName, String uploaderName) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        // 이미지들 추가
        for (byte[] img : images) {
            ObjectNode part = parts.addObject();
            ObjectNode inlineData = part.putObject("inline_data");
            inlineData.put("mime_type", "image/png");
            inlineData.put("data", Base64.getEncoder().encodeToString(img));
        }

        // 프롬프트 (P1: 메타데이터 포함, P2: 이미지이므로 항상 비전 모드)
        String prompt = """
                이 이미지들은 PDF 페이지를 렌더링한 것이다. 한국어 기업분석 자료이다.

                ## 메타데이터
                - 파일명: %s
                - 작성자: %s

                ## 추출 규칙
                1. 먼저 이 PDF가 분석하는 **대상 기업/종목명**을 파악하라. 첫 줄에 "분석 대상: [기업명]"으로 명시하라.
                2. 모든 수치, 날짜, 고유명사는 원문 그대로 정확히 보존 (단위 포함)
                3. 표/차트 데이터는 마크다운 테이블로 변환. 해설 컬럼 추가하지 마라
                4. 핵심 논지, 투자 포인트, 리스크 요인을 섹션으로 구분
                5. 불필요한 서식(페이지 번호, 헤더/푸터, 면책조항)은 제거
                6. 작성자 개인정보(학번, 이름, 소속)는 제거
                7. PDF에 없는 내용을 추가하지 마라. 추출만 하라
                """.formatted(fileName, uploaderName);

        parts.addObject().put("text", prompt);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(res.body());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Gemini 에러 " + res.statusCode() + ": " + res.body().substring(0, Math.min(200, res.body().length())));
        }

        String text = result.at("/candidates/0/content/parts/0/text").asText();
        int inputTokens = result.at("/usageMetadata/promptTokenCount").asInt();
        int outputTokens = result.at("/usageMetadata/candidatesTokenCount").asInt();
        System.out.printf("  [Gemini] 토큰: %d→%d, 비용: $0%n", inputTokens, outputTokens);

        return text;
    }

    // ══════════════════════════════════════════
    // Step 2: Cerebras 글 생성 (P3, P4 적용)
    // ══════════════════════════════════════════

    static final String SYSTEM_PROMPT = """
            당신은 대학 투자 동아리 게시판에 올릴 기업분석 글을 쓰는 AI다.

            ## 역할
            PDF에서 추출한 텍스트를 기반으로, 웹 검색으로 최신 맥락을 보강하여 분석 글을 마크다운으로 작성한다.

            ## 톤
            - 동아리 내부 게시판 글 톤. "~이다", "~한다" 체.
            - 확신 있는 문장. "~로 보입니다", "~될 것으로 예상됩니다" 같은 회피형 쓰지 마라.

            ## 핵심 원칙

            ### 1. PDF 충실성
            - PDF의 논조와 관점을 살려라. PDF가 긍정적이면 긍정적, 비판적이면 비판적.
            - PDF 원문의 수치를 왜곡하지 마라.

            ### 2. 수치 정확성 (가장 중요)
            - PDF에 있는 수치: 그대로 사용. 출처 표기 불필요.
            - 웹 검색에서 얻은 수치: 반드시 검색 결과에 표기된 [매체: OO] 매체명을 괄호로 표기.
            - 검색 결과에서 확인되지 않은 수치를 만들어내지 마라.
            - 여러 전망이 있으면 범위로 표기하라.
            - "훌쩍 넘는다", "폭발적으로" 같은 수치 과장 수식어 금지.
            - 웹 검색 수치를 직접 계산하거나 조합하지 마라. 검색 결과에 나온 수치를 그대로 인용하라.

            ### 3. 출처 규칙 (매우 중요)
            - 검색 결과에 [매체: OO] 형태로 매체명이 표기되어 있다.
            - 글에서 인용할 때 이 매체명을 그대로 사용하라.
            - 매체명을 번역하거나 다른 이름으로 바꾸지 마라. 검색 결과에 없는 매체명을 만들어내지 마라.
            - "증권가에서는", "업계에서는" 같은 모호한 주어 금지.

            ### 4. 고유명사 정확성
            - 제품명, 기업명, 기술명은 공식 표기를 사용하라.
            - 영문 고유명사는 원어 표기를 병기하라.

            ## 작성 규칙
            1. 핵심 논지를 상단에 배치 — 근거는 이후 전개
            2. 숫자를 나열하지 말고, 숫자 간의 관계와 변화를 짚어라
            3. PDF에 없는 외부 정보는 웹 검색으로 보강하되, PDF 데이터와 명확히 구분하라

            ## 금지 사항
            - 인사말, 서명, 원작자 이름/소속 출력 금지
            - 마지막에 앞 내용 요약 반복하는 "결론"/"요약" 섹션 금지. 마지막 섹션도 새로운 관점을 담아라
            - 투자 전략, 매수/매도 판단 금지
            - PDF에 없는 수치를 만들어내거나, 검색 결과를 과장하는 것 금지
            - 같은 검색어로 반복 검색하지 마라

            ## 도구
            - web_search: 종목 관련 최신 뉴스/시세/동향을 검색한다. 3~5회 사용. 핵심 주제별 1회씩.

            ## 출력 형식
            - 마크다운. 첫 줄은 ## 제목.
            - 제목은 관점이 드러나는 분석적 제목. 회사명 + 핵심 논점.
            - H3(###)으로 섹션 구분. 표/리스트/굵은글씨로 구조화.
            """;

    static String getMediaName(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return url;
            host = host.replaceFirst("^www\\.", "");
            if (MEDIA_MAP.containsKey(host)) return MEDIA_MAP.get(host);
            // 서브도메인 제거 후 재시도
            String[] parts = host.split("\\.");
            if (parts.length > 2) {
                String base = parts[parts.length - 2] + "." + parts[parts.length - 1];
                if (MEDIA_MAP.containsKey(base)) return MEDIA_MAP.get(base);
            }
            return host;
        } catch (Exception e) {
            return url;
        }
    }

    static String webSearch(String query) throws Exception {
        String url = "https://openapi.naver.com/v1/search/news.json?query=" +
                java.net.URLEncoder.encode(query, "UTF-8") + "&display=5&sort=date";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Naver-Client-Id", NAVER_CLIENT_ID)
                .header("X-Naver-Client-Secret", NAVER_CLIENT_SECRET)
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode data = mapper.readTree(res.body());
        JsonNode items = data.get("items");
        if (items == null || items.isEmpty()) return "검색 결과 없음";

        StringBuilder sb = new StringBuilder();
        for (JsonNode item : items) {
            String title = item.get("title").asText().replaceAll("<[^>]*>", "");
            String desc = item.get("description").asText().replaceAll("<[^>]*>", "");
            String link = item.has("originallink") ? item.get("originallink").asText() : item.get("link").asText();
            String media = getMediaName(link);
            String pubDate = item.get("pubDate").asText();
            sb.append("- [매체: ").append(media).append("] ").append(title)
                    .append(" — ").append(desc).append(" (").append(pubDate).append(")\n");
        }
        return sb.toString();
    }

    static String generateWithCerebras(String pdfText, String uploaderName) throws Exception {
        String userMessage = """
                다음은 PDF에서 추출한 텍스트이다. 웹 검색으로 최신 맥락을 보강하여 분석 글을 작성하라.
                작성자(%s)의 이름은 글에 포함하지 마라.

                ---
                %s
                ---
                """.formatted(uploaderName, pdfText);

        // 도구 정의
        ObjectNode toolDef = mapper.createObjectNode();
        toolDef.put("type", "function");
        ObjectNode fn = toolDef.putObject("function");
        fn.put("name", "web_search");
        fn.put("description", "웹에서 최신 뉴스, 시세, 기업 정보를 검색한다. 3~5회 사용. 핵심 주제별 1회씩.");
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode queryProp = props.putObject("query");
        queryProp.put("type", "string");
        queryProp.put("description", "검색 쿼리");
        params.putArray("required").add("query");

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(mapper.createObjectNode().put("role", "system").put("content", SYSTEM_PROMPT));
        messages.add(mapper.createObjectNode().put("role", "user").put("content", userMessage));

        int totalInput = 0, totalOutput = 0, round = 0;
        List<String> searches = new ArrayList<>();

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
            JsonNode result = mapper.readTree(res.body());

            JsonNode choice = result.at("/choices/0");
            totalInput += result.at("/usage/prompt_tokens").asInt(0);
            totalOutput += result.at("/usage/completion_tokens").asInt(0);

            JsonNode toolCalls = choice.at("/message/tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                // 어시스턴트 메시지 추가
                ObjectNode assistantMsg = mapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("tool_calls", toolCalls);
                // content가 null이면 빈 문자열로
                String contentText = choice.at("/message/content").asText(null);
                if (contentText != null) assistantMsg.put("content", contentText);
                messages.add(assistantMsg);

                for (JsonNode tc : toolCalls) {
                    String args = tc.at("/function/arguments").asText();
                    JsonNode argsNode = mapper.readTree(args);
                    String query = argsNode.get("query").asText();
                    searches.add(query);
                    System.out.printf("  [검색] %s%n", query);
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
            System.out.printf("  [Cerebras] 라운드: %d, 검색: %d회, 토큰: %d→%d%n",
                    round, searches.size(), totalInput, totalOutput);
            return output;
        }
        throw new RuntimeException("최대 라운드 초과");
    }

    // ══════════════════════════════════════════
    // 일괄 테스트
    // ══════════════════════════════════════════

    @Test
    void batchTest() throws Exception {
        String[] pdfFiles = {
                "삼성전자_기업분석.pdf",
                "SK하이닉스_기업분석.pdf",
                "현대자동차_기업분석.pdf",
                "NAVER_기업분석.pdf",
                "카카오_기업분석.pdf",
                "LG에너지솔루션_기업분석.pdf",
        };

        StringBuilder summary = new StringBuilder();
        summary.append("| 종목 | Step1 | Step2 | 검색 | 총시간 | 종목식별 | 상태 |\n");
        summary.append("|------|-------|-------|------|--------|----------|------|\n");

        for (int i = 0; i < pdfFiles.length; i++) {
            String pdfFile = pdfFiles[i];
            String label = pdfFile.replace("_기업분석.pdf", "");
            // P1: 메타데이터 — 실제 서비스에서는 DB에서 조회
            String uploaderName = "테스트사용자";
            Path pdfPath = PDF_DIR.resolve(pdfFile);

            System.out.printf("%n{'='.repeat(60)}%n[%d/%d] %s%n{'='.repeat(60)}%n",
                    i + 1, pdfFiles.length, label);

            try {
                // Step 1: PDF → PNG → Gemini
                System.out.println("[Step 1] PDF→PNG 변환 + Gemini 추출...");
                long t1 = System.currentTimeMillis();
                List<byte[]> images = pdfToImages(pdfPath, 50);
                System.out.printf("  PNG 변환: %d페이지, %.1fs%n",
                        images.size(), (System.currentTimeMillis() - t1) / 1000.0);

                long t2 = System.currentTimeMillis();
                String extractedText = extractWithGemini(images, pdfFile, uploaderName);
                double step1Time = (System.currentTimeMillis() - t1) / 1000.0;
                System.out.printf("  Gemini 완료: %.1fs%n", step1Time);

                // 추출 텍스트에서 종목명 확인
                String firstLine = extractedText.lines().findFirst().orElse("");
                System.out.printf("  종목 식별: %s%n", firstLine);

                Files.writeString(OUTPUT_DIR.resolve("v4-extract-" + label + ".md"), extractedText);

                // Gemini rate limit 대비
                Thread.sleep(7000);

                // Step 2: Cerebras 생성
                System.out.println("[Step 2] Cerebras 생성...");
                long t3 = System.currentTimeMillis();
                String article = generateWithCerebras(extractedText, uploaderName);
                double step2Time = (System.currentTimeMillis() - t3) / 1000.0;
                System.out.printf("  Cerebras 완료: %.1fs%n", step2Time);

                Files.writeString(OUTPUT_DIR.resolve("v4-article-" + label + ".md"), article);

                // 글 미리보기
                System.out.println("--- 미리보기 ---");
                System.out.println(article.substring(0, Math.min(200, article.length())));
                System.out.println("...\n");

                summary.append(String.format("| %s | %.1fs | %.1fs | - | %.1fs | %s | ✅ |%n",
                        label, step1Time, step2Time, step1Time + step2Time,
                        firstLine.length() > 30 ? firstLine.substring(0, 30) + "..." : firstLine));

            } catch (Exception e) {
                System.err.printf("  ❌ 에러: %s%n", e.getMessage());
                summary.append(String.format("| %s | - | - | - | - | - | ❌ %s |%n",
                        label, e.getMessage().substring(0, Math.min(30, e.getMessage().length()))));
            }
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== v4 일괄 테스트 결과 ===");
        System.out.println("=".repeat(60));
        System.out.println(summary);

        Files.writeString(OUTPUT_DIR.resolve("v4-summary.md"), summary.toString());
    }
}
