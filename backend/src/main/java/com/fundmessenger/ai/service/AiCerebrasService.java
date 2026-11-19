package com.fundmessenger.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

/**
 * Cerebras Qwen 3 235B — 추출 텍스트 + 웹검색으로 분석 글 생성 (Step 2)
 * Function calling (web_search) 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCerebrasService {

    private final CerebrasClient cerebrasClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${naver.search.client-id:}")
    private String naverClientId;

    @Value("${naver.search.client-secret:}")
    private String naverClientSecret;

    @Value("${cerebras.model:qwen-3-235b-a22b-instruct-2507}")
    private String model;

    private static final int MAX_ROUNDS = 10;

    // 매체명 매핑 사전
    private static final Map<String, String> MEDIA_MAP = Map.ofEntries(
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
            Map.entry("businesspost.co.kr", "비즈니스포스트"), Map.entry("news1.kr", "뉴스1"),
            Map.entry("newspim.com", "뉴스핌"), Map.entry("thebell.co.kr", "더벨"),
            Map.entry("wowtv.co.kr", "한국경제TV"), Map.entry("sbs.co.kr", "SBS뉴스"),
            Map.entry("kbs.co.kr", "KBS뉴스"), Map.entry("mbc.co.kr", "MBC뉴스"),
            Map.entry("ytn.co.kr", "YTN"), Map.entry("economist.co.kr", "이코노미스트"),
            Map.entry("mediapen.com", "미디어펜"), Map.entry("finomy.com", "피노미"),
            Map.entry("infomax.co.kr", "연합인포맥스"), Map.entry("tvdaily.co.kr", "TV데일리")
    );

    private static final String SYSTEM_PROMPT = """
            당신은 대학 투자 동아리 게시판에 올릴 기업분석 글을 쓰는 AI다.

            ## 역할
            PDF에서 추출한 텍스트를 기반으로, 웹 검색으로 최신 맥락을 보강하여 분석 글을 마크다운으로 작성한다.

            ## 톤
            - "~이다", "~한다" 체.
            - 반드시 한국어로 작성. 영어 전문용어는 한국어 표기 후 괄호로 병기 (예: 망간(Manganese)). 한영 혼합 금지.

            ## 핵심 원칙
            1. PDF의 논조와 관점을 살려라.
            2. 수치 정확성: PDF 수치 그대로, 웹 검색 수치는 매체명 표기. 수치를 직접 계산/조합하지 마라. "비합리적", "검증 필요" 같은 메타 코멘트 금지.
            3. 출처: 검색 결과의 [매체: OO] 매체명 그대로 사용. 매체명을 번역하거나 만들어내지 마라.

            ## 도구
            - web_search: 3~5회 사용. 핵심 주제별 1회씩.

            ## 차트 블록
            - 수치 비교가 명확한 경우 차트를 삽입하라. 글당 1~3개.
            - 10가지 유형: bar, line, pie, stacked-bar, waterfall, flow, timeline, gauge, treemap, radar
            - **레이아웃 필수 지정**: 모든 차트 JSON에 반드시 "layout" 키를 포함하라.
              - "full": 전체 너비 (데이터가 많거나 복잡한 차트)
              - "half-left": 왼쪽 절반 + 오른쪽에 "caption" 텍스트 (차트 해설)
              - "half-right": 오른쪽 절반 + 왼쪽에 "caption" 텍스트 (차트 해설)
              - "half-center": 중앙 절반 너비
            - 다양한 레이아웃을 혼합하여 시각적 다채로움을 연출하라. full만 반복 금지.
            - 차트 data 키는 반드시 label/value를 사용 (x/y 금지)
            - markdown 내에 ```chart-TYPE 코드블록으로 JSON 삽입.
              예: ```chart-bar
              {"layout":"half-left","caption":"1분기 대비 2분기 매출이 20% 증가하며 성장세를 보였다.","title":"분기별 매출","data":[{"label":"1Q","value":100},{"label":"2Q","value":120}],"unit":"억원"}
              ```
            - stacked-bar 예: ```chart-stacked-bar
              {"layout":"full","title":"부문별 비교","categories":["1Q","2Q"],"series":[{"name":"반도체","data":[50,60]},{"name":"디스플레이","data":[30,40]}]}
              ```
            - pie 예: ```chart-pie
              {"layout":"half-right","caption":"반도체 부문이 전체 매출의 60%를 차지하며 핵심 수익원이다.","title":"매출 구성","data":[{"label":"반도체","value":60},{"label":"디스플레이","value":25},{"label":"기타","value":15}],"unit":"%"}
              ```
            - 차트가 있어도 본문 길이를 줄이지 마라. 차트는 시각적 보충이다.

            ## 금지
            - 인사말, 서명 금지. 결론 반복 금지. 투자 조언 금지.

            ## 출력
            - 마크다운. 첫 줄 ## 제목. H3으로 섹션 구분.

            /no_think""";

    private static final String TOOLS_JSON = """
            [{"type":"function","function":{"name":"web_search","description":"웹 검색. 3~5회.","parameters":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}}]""";

    /**
     * 추출 텍스트로 분석 글 생성 (function calling 포함)
     */
    public String generateArticle(String extractedText) {
        if (!cerebrasClient.isAvailable()) {
            throw new ExternalServiceException("Cerebras", "API 키가 설정되지 않았습니다");
        }

        log.info("Cerebras 글 생성 시작 (텍스트 {}자)", extractedText.length());

        try {
            // OpenAI 호환 API를 직접 호출 (function calling 루프)
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            messages.add(Map.of("role", "user", "content", "PDF 추출 텍스트:\n\n---\n" + extractedText + "\n---"));

            JsonNode tools = objectMapper.readTree(TOOLS_JSON);

            for (int round = 0; round < MAX_ROUNDS; round++) {
                JsonNode response = callCerebrasChat(messages, tools);
                if (response == null) {
                    throw new ExternalServiceException("Cerebras", "API 응답 없음");
                }

                JsonNode choice = response.path("choices").get(0);
                JsonNode message = choice.path("message");
                String finishReason = choice.path("finish_reason").asText();

                // 토큰 로깅
                JsonNode usage = response.path("usage");
                if (!usage.isMissingNode()) {
                    log.debug("Cerebras R{}: input={}, output={}, finish={}",
                            round + 1, usage.path("prompt_tokens").asInt(),
                            usage.path("completion_tokens").asInt(), finishReason);
                }

                // tool_calls 처리
                if ("tool_calls".equals(finishReason) || message.has("tool_calls")) {
                    messages.add(objectMapper.convertValue(message, Map.class));

                    JsonNode toolCalls = message.path("tool_calls");
                    for (JsonNode tc : toolCalls) {
                        String toolName = tc.path("function").path("name").asText();
                        String argsStr = tc.path("function").path("arguments").asText();
                        String toolCallId = tc.path("id").asText();

                        if ("web_search".equals(toolName)) {
                            JsonNode args = objectMapper.readTree(argsStr);
                            String query = args.path("query").asText();
                            log.debug("  web_search: \"{}\"", query);
                            String searchResult = webSearch(query);
                            messages.add(Map.of("role", "tool", "tool_call_id", toolCallId, "content", searchResult));
                        }
                    }
                    continue;
                }

                // 최종 응답
                String content = message.path("content").asText();
                log.info("Cerebras 글 생성 완료: {}라운드, {}자", round + 1, content.length());
                return content;
            }

            throw new ExternalServiceException("Cerebras", "최대 라운드 초과");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Cerebras", "글 생성 실패: " + e.getMessage());
        }
    }

    /**
     * Cerebras Chat Completions API 직접 호출 (tools 포함)
     */
    private JsonNode callCerebrasChat(List<Map<String, Object>> messages, JsonNode tools) {
        // CerebrasClient의 라운드로빈 키를 활용하기 위해 내부 호출
        // CerebrasClient.generateJson은 tool calling을 지원하지 않으므로 별도 구현
        try {
            // CerebrasClient에서 키를 가져올 수 없으므로 직접 호출
            // → CerebrasClient에 raw chat 메서드를 추가하는 것이 이상적이지만,
            //   현재는 동일한 설정값을 사용하여 직접 호출
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("max_tokens", 8000);

            // 사용자 요청 우선순위 — 파이프라인/봇보다 우선 처리
            return cerebrasClient.generateRawUser(body);
        } catch (Exception e) {
            log.error("Cerebras Chat API 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Naver News API 검색
     */
    private String webSearch(String query) {
        if (naverClientId == null || naverClientId.isBlank()) {
            return "검색 API 미설정";
        }

        try {
            String url = "https://openapi.naver.com/v1/search/news.json?query="
                    + java.net.URLEncoder.encode(query, "UTF-8") + "&display=5&sort=date";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", naverClientId);
            headers.set("X-Naver-Client-Secret", naverClientSecret);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return "검색 결과 없음";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");
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
            log.warn("웹 검색 실패: {}", e.getMessage());
            return "검색 오류";
        }
    }

    private String getMediaName(String url) {
        try {
            String hostname = new URI(url).getHost();
            if (hostname == null) return "언론보도";
            hostname = hostname.replaceFirst("^www\\.", "");

            if (MEDIA_MAP.containsKey(hostname)) return MEDIA_MAP.get(hostname);

            // 서브도메인 제거 후 재시도
            String[] parts = hostname.split("\\.");
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
