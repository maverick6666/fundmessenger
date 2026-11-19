package com.fundmessenger.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.PostComment;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.ClubSettings;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundmessenger.admin.entity.GlobalSettings;
import com.fundmessenger.admin.repository.GlobalSettingsRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCommentBotService {

    private final ClubSettingsRepository settingsRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final StockRepository stockRepository;
    private final StockNewsCouplingRepository couplingRepository;
    private final CanonicalNewsRepository canonicalNewsRepository;
    private final NewsCommentRepository newsCommentRepository;
    private final StockThreadRepository threadRepository;
    private final ThreadReplyRepository threadReplyRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final CerebrasClient cerebrasClient;
    private final GlobalSettingsRepository globalSettingsRepository;
    private final BoardRepository boardRepository;

    // ── 페르소나 정의 ──

    public enum BotPersona {
        BULLISH("황소", "\uD83D\uDC02"),
        BEARISH("불곰", "\uD83D\uDC3B"),
        METAPHOR("물개", "\uD83E\uDDAD");

        @Getter
        private final String displayName;
        @Getter
        private final String emoji;

        BotPersona(String displayName, String emoji) {
            this.displayName = displayName;
            this.emoji = emoji;
        }
    }

    // ── 콘텐츠 타입 ──

    public enum ContentType {
        THREAD, THREAD_REPLY, BOARD_COMMENT, NEWS_COMMENT
    }

    // 라운드로빈 인덱스 (동아리별)
    private final Map<Long, Integer> personaIndex = new HashMap<>();

    // CB-3: 직전 종목 쿨다운 (동아리별 이전 종목 ID)
    private final Map<Long, Long> previousStockId = new HashMap<>();

    // ── 메인 실행 ──

    @Transactional
    public Map<String, Object> executeRound(Long clubId, BotPersona forcedPersona) {
        return executeRound(clubId, forcedPersona, null);
    }

    @Transactional
    public Map<String, Object> executeRound(Long clubId, BotPersona forcedPersona, ContentType forcedType) {
        ClubSettings settings = settingsRepository.findById(clubId).orElse(null);
        if (settings == null || !settings.isAiCommentBotEnabled()) {
            log.info("AI 댓글봇 비활성 (clubId={})", clubId);
            return Map.of("skipped", true, "reason", "봇 비활성");
        }

        if (!cerebrasClient.isAvailable()) {
            log.warn("Cerebras 키 미설정 — 댓글봇 실행 스킵");
            return Map.of("skipped", true, "reason", "Cerebras 키 미설정");
        }

        String scope = settings.getAiCommentBotScope();
        BotPersona persona = forcedPersona != null ? forcedPersona : nextPersona(clubId);
        ClubMembership botMembership = getOrCreateBotMembership(clubId, persona);

        try {
            // ★ 우선순위 1: AI 콘텐츠에 달린 사람 답글에 응답 (반응형)
            Map<String, Object> reactiveResult = handlePendingResponses(clubId, persona, botMembership);
            if (reactiveResult != null) {
                return reactiveResult;
            }

            // 우선순위 2: 정규 콘텐츠 생성
            ContentType type = forcedType != null ? forcedType : pickRandomType(scope);
            if (forcedType != null && !isTypeAvailable(forcedType, scope)) {
                type = pickRandomType(scope);
            }

            return switch (type) {
                case THREAD -> executeCreateThread(clubId, persona, botMembership);
                case THREAD_REPLY -> executeThreadReply(clubId, persona, botMembership);
                case BOARD_COMMENT -> executeBoard(clubId, persona, botMembership);
                case NEWS_COMMENT -> executeNewsComment(clubId, persona, botMembership);
            };
        } catch (Exception e) {
            log.error("AI 댓글봇 실행 실패 (clubId={}, persona={}, type={}): {}",
                    clubId, persona.name(), forcedType, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    private ContentType pickRandomType(String scope) {
        List<ContentType> available = getAvailableTypes(scope);
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private List<ContentType> getAvailableTypes(String scope) {
        return switch (scope) {
            case "JONGTOBANG_ONLY" -> List.of(ContentType.THREAD, ContentType.THREAD_REPLY, ContentType.NEWS_COMMENT);
            case "BOARD_ONLY" -> List.of(ContentType.BOARD_COMMENT);
            case "BOTH" -> List.of(ContentType.THREAD, ContentType.THREAD_REPLY, ContentType.BOARD_COMMENT, ContentType.NEWS_COMMENT);
            default -> List.of(ContentType.THREAD, ContentType.THREAD_REPLY, ContentType.BOARD_COMMENT, ContentType.NEWS_COMMENT);
        };
    }

    private boolean isTypeAvailable(ContentType type, String scope) {
        return getAvailableTypes(scope).contains(type);
    }

    // ── 종목 선택 (CB-2: 변동률15 + 주목순15 합집합, CB-3: 직전 종목 쿨다운) ──

    private Stock pickStock(Long clubId, boolean preferWithCoupling) {
        // CB-2: 변동률 상위 15 + 주목순 상위 15 합집합
        List<Stock> changeRateTop = stockRepository.findByChangeRateDesc(PageRequest.of(0, 15));
        List<Stock> attentionTop = stockRepository.findByAttention(PageRequest.of(0, 15));

        // 합집합 (중복 제거)
        LinkedHashSet<Stock> poolSet = new LinkedHashSet<>(changeRateTop);
        poolSet.addAll(attentionTop);
        List<Stock> pool = new ArrayList<>(poolSet);

        if (pool.isEmpty()) return null;

        // coupling 우선 필터
        if (preferWithCoupling) {
            List<Long> coupledStockIds = couplingRepository.findDistinctStockIds();
            if (!coupledStockIds.isEmpty()) {
                List<Stock> withCoupling = pool.stream()
                        .filter(s -> coupledStockIds.contains(s.getId()))
                        .collect(Collectors.toList());
                if (!withCoupling.isEmpty()) {
                    pool = withCoupling;
                }
            }
        }

        // CB-3: 직전 종목 쿨다운 (같은 종목 연속 방지)
        Long prevId = previousStockId.get(clubId);
        if (prevId != null && pool.size() > 1) {
            pool = pool.stream()
                    .filter(s -> !s.getId().equals(prevId))
                    .collect(Collectors.toList());
        }

        if (pool.isEmpty()) return null;
        Stock selected = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        previousStockId.put(clubId, selected.getId());
        return selected;
    }

    // ── 종토방: 새 스레드 ──

    private Map<String, Object> executeCreateThread(Long clubId, BotPersona persona, ClubMembership botMembership) {
        Stock stock = pickStock(clubId, false);
        if (stock == null) {
            return Map.of("skipped", true, "reason", "주목 종목 없음");
        }

        List<StockNewsCoupling> couplings = couplingRepository.findByStockIdSentimentFirst(
                stock.getId(), PageRequest.of(0, 5));
        String context = collectJongtobangContext(stock, couplings);

        String systemPrompt = getPersonaSystemPrompt(persona, "thread");
        String userPrompt = context + "\n위 종목에 대해 트윗 스타일(3~5줄)의 짧은 스레드를 작성하세요. "
                + "시세 한줄 인용을 자연스럽게 포함하세요. JSON으로 {\"title\": \"...\", \"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) {
            return Map.of("error", "Cerebras 응답 없음");
        }

        String title = result.has("title") ? result.get("title").asText() : stock.getStockName() + " 한마디";
        String content = result.has("content") ? result.get("content").asText() : "";
        if (title.length() > 200) title = title.substring(0, 200);
        if (content.length() > 5000) content = content.substring(0, 5000);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        StockThread thread = StockThread.builder()
                .club(club)
                .stock(stock)
                .author(botMembership)
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .market(stock.getMarket())
                .title(title)
                .content(content)
                .aiGenerated(true)
                .build();
        threadRepository.save(thread);

        log.info("AI 스레드 생성: clubId={}, stock={}, persona={}, threadId={}",
                clubId, stock.getStockName(), persona.name(), thread.getId());

        return Map.of(
                "type", "THREAD",
                "content", content,
                "personaName", persona.getDisplayName(),
                "personaEmoji", persona.getEmoji(),
                "targetInfo", stock.getStockName() + " 스레드"
        );
    }

    // ── 종토방: 스레드 답글 ──

    private Map<String, Object> executeThreadReply(Long clubId, BotPersona persona, ClubMembership botMembership) {
        // 최근 스레드 중 답글이 적은 것 선택
        List<StockThread> recentThreads = threadRepository.findByClubIdOrderByCreatedAtDesc(
                clubId, PageRequest.of(0, 20)).getContent();

        if (recentThreads.isEmpty()) {
            log.info("답글 대상 스레드 없음 — 새 스레드로 폴백 (clubId={})", clubId);
            return executeCreateThread(clubId, persona, botMembership);
        }

        // 봇이 이미 답글 단 스레드 제외, 답글 3개 미만인 스레드 우선
        List<StockThread> candidates = recentThreads.stream()
                .filter(t -> !hasBotReply(t.getId(), botMembership.getId()))
                .filter(t -> t.getReplyCount() < 3)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // 조건 완화: 봇 답글만 없으면 OK
            candidates = recentThreads.stream()
                    .filter(t -> !hasBotReply(t.getId(), botMembership.getId()))
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            log.info("답글 가능 스레드 없음 — 새 스레드로 폴백 (clubId={})", clubId);
            return executeCreateThread(clubId, persona, botMembership);
        }

        StockThread target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // 컨텍스트: 스레드 제목+본문+기존 답글
        StringBuilder context = new StringBuilder();
        context.append("[원글]\n제목: ").append(target.getTitle()).append("\n");
        context.append("내용: ").append(target.getContent()).append("\n");
        context.append("종목: ").append(target.getStockName()).append("\n\n");

        // 기존 답글 2개까지 포함
        List<ThreadReply> existingReplies = threadReplyRepository.findByThreadIdOrderByCreatedAtAsc(
                target.getId(), PageRequest.of(0, 2)).getContent();
        if (!existingReplies.isEmpty()) {
            context.append("[기존 답글]\n");
            for (ThreadReply r : existingReplies) {
                context.append("- ").append(r.getAuthor().getName()).append(": ").append(r.getContent()).append("\n");
            }
        }

        // CB-1: 라이벌 반박 체크
        String rivalContent = findRivalThreadReply(target.getId(), persona);
        if (rivalContent != null && !hasAlreadyRebutted(target.getId(), botMembership.getId(), "thread")) {
            // 50% 확률로 반박
            if (ThreadLocalRandom.current().nextBoolean()) {
                context.append("\n[라이벌 의견 — 반박하세요]\n").append(rivalContent).append("\n");
            }
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "comment");
        String userPrompt = context + "\n위 스레드에 대해 1~3줄의 답글을 작성하세요. "
                + "기존 답글이 있으면 대화 흐름을 이어가세요. JSON으로 {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) {
            return Map.of("error", "Cerebras 응답 없음");
        }

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 2000) content = content.substring(0, 2000);

        ThreadReply reply = ThreadReply.builder()
                .thread(target)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        threadReplyRepository.save(reply);

        // replyCount 갱신
        target.setReplyCount(target.getReplyCount() + 1);
        threadRepository.save(target);

        log.info("AI 스레드 답글 생성: threadId={}, persona={}, rival={}", target.getId(), persona.name(), rivalContent != null);

        return Map.of(
                "type", "THREAD_REPLY",
                "content", content,
                "personaName", persona.getDisplayName(),
                "personaEmoji", persona.getEmoji(),
                "targetInfo", target.getTitle() + " 답글"
        );
    }

    private boolean hasBotReply(Long threadId, Long botMembershipId) {
        return threadReplyRepository.findByThreadIdOrderByCreatedAtAsc(threadId, PageRequest.of(0, 50))
                .getContent().stream()
                .anyMatch(r -> r.getAuthor().getId().equals(botMembershipId));
    }

    // ── 종토방: 뉴스 댓글 ──

    private Map<String, Object> executeNewsComment(Long clubId, BotPersona persona, ClubMembership botMembership) {
        // coupling 있는 종목 우선 선택
        Stock stock = pickStock(clubId, true);
        if (stock == null) {
            log.info("주목 종목 없음 — 뉴스 댓글 스킵");
            return Map.of("skipped", true, "reason", "주목 종목 없음");
        }

        List<StockNewsCoupling> couplings = couplingRepository.findByStockIdSentimentFirst(
                stock.getId(), PageRequest.of(0, 5));

        if (couplings.isEmpty()) {
            log.info("뉴스 coupling 없음 — 스레드로 폴백 (stockId={})", stock.getId());
            return executeCreateThread(clubId, persona, botMembership);
        }

        StockNewsCoupling target = couplings.get(ThreadLocalRandom.current().nextInt(couplings.size()));
        String context = collectJongtobangContext(stock, couplings);

        String newsTitle = target.getCanonicalNews() != null ? target.getCanonicalNews().getTitle() : "뉴스";

        // CB-1: 라이벌 반박 체크
        String rivalContent = findRivalNewsComment(target.getId(), persona);
        if (rivalContent != null && !hasAlreadyRebutted(target.getId(), botMembership.getId(), "news")) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                context += "\n\n[라이벌 의견 — 반박하세요]\n" + rivalContent;
            }
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "comment");
        String userPrompt = context + "\n뉴스 제목: " + newsTitle
                + "\n\n위 뉴스에 대해 1~3줄의 짧은 댓글을 작성하세요. JSON으로 {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) {
            return Map.of("error", "Cerebras 응답 없음");
        }

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 1000) content = content.substring(0, 1000);

        NewsComment comment = NewsComment.builder()
                .coupling(target)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        newsCommentRepository.save(comment);

        target.setCommentCount(target.getCommentCount() + 1);
        couplingRepository.save(target);

        log.info("AI 뉴스 댓글 생성: couplingId={}, persona={}, rival={}", target.getId(), persona.name(), rivalContent != null);

        return Map.of(
                "type", "NEWS_COMMENT",
                "content", content,
                "personaName", persona.getDisplayName(),
                "personaEmoji", persona.getEmoji(),
                "targetInfo", newsTitle
        );
    }

    // ── 게시판 댓글 ──

    private Map<String, Object> executeBoard(Long clubId, BotPersona persona, ClubMembership botMembership) {
        // 최근 7일 내 글 중 공지사항 제외, AI 댓글 미작성 글
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Post> recentPosts = postRepository.findWithFilters(clubId, null, null, PageRequest.of(0, 50))
                .getContent().stream()
                .filter(p -> p.getCreatedAt().isAfter(since))
                .filter(p -> p.getBoards().stream().noneMatch(b -> b.getBoardType() == BoardType.AI))
                .filter(p -> !hasSamePersonaComment(p.getId(), botMembership.getId()))
                .collect(Collectors.toList());

        if (recentPosts.isEmpty()) {
            log.info("게시판 대상 글 없음 (clubId={})", clubId);
            return Map.of("skipped", true, "reason", "게시판 대상 글 없음");
        }

        Post target = recentPosts.get(ThreadLocalRandom.current().nextInt(recentPosts.size()));

        String context = collectBoardContext(target);

        // CB-1: 라이벌 반박 체크
        String rivalContent = findRivalPostComment(target.getId(), persona);
        if (rivalContent != null && !hasAlreadyRebutted(target.getId(), botMembership.getId(), "post")) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                context += "\n\n[라이벌 의견 — 반박하세요]\n" + rivalContent;
            }
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "comment");
        String userPrompt = context + "\n\n위 게시글에 대해 1~3줄의 댓글을 작성하세요. "
                + "글의 주제에 맞게 반응하세요. JSON으로 {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) {
            return Map.of("error", "Cerebras 응답 없음");
        }

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 500) content = content.substring(0, 500);

        PostComment comment = PostComment.builder()
                .post(target)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        postCommentRepository.save(comment);

        log.info("AI 게시판 댓글 생성: postId={}, persona={}, rival={}", target.getId(), persona.name(), rivalContent != null);

        return Map.of(
                "type", "POST_COMMENT",
                "content", content,
                "personaName", persona.getDisplayName(),
                "personaEmoji", persona.getEmoji(),
                "targetInfo", target.getTitle()
        );
    }

    // ── 컨텍스트 수집 ──

    private String collectJongtobangContext(Stock stock, List<StockNewsCoupling> couplings) {
        StringBuilder sb = new StringBuilder();
        sb.append("종목: ").append(stock.getStockName()).append(" (").append(stock.getStockCode()).append(")\n");
        sb.append("시장: ").append(stock.getMarket()).append("\n");
        if (stock.getCurrentPrice() != null) {
            String currency = isKoreanMarket(stock.getMarket()) ? "원" : "달러";
            sb.append("현재가: ").append(stock.getCurrentPrice()).append(currency);
            if (stock.getChangeRate() != null) {
                sb.append(" (").append(stock.getChangeRate().signum() > 0 ? "+" : "").append(stock.getChangeRate()).append("%)");
            }
            sb.append("\n");
        }

        sb.append("\n[최근 뉴스]\n");
        int newsCount = 0;
        for (StockNewsCoupling c : couplings) {
            if (newsCount >= 3) break;
            if (c.getCanonicalNews() != null) {
                sb.append("- ").append(c.getCanonicalNews().getTitle()).append("\n");
                newsCount++;
            }
        }
        if (newsCount == 0) sb.append("- 최근 뉴스 없음\n");

        return sb.toString();
    }

    private boolean isKoreanMarket(String market) {
        return market != null && (market.contains("KOSPI") || market.contains("KOSDAQ")
                || market.contains("KRX") || "KR".equalsIgnoreCase(market));
    }

    private String collectBoardContext(Post post) {
        StringBuilder sb = new StringBuilder();
        String boardNames = post.getBoards().stream().map(b -> b.getName()).collect(java.util.stream.Collectors.joining(", "));
        sb.append("게시판: ").append(boardNames).append("\n");
        sb.append("제목: ").append(post.getTitle()).append("\n");
        sb.append("작성자: ").append(post.getAuthor().getName()).append("\n\n");

        String body = post.getContent();
        if (body != null && body.length() > 3000) {
            body = body.substring(0, 3000) + "... (이하 생략)";
        }
        sb.append("[본문]\n").append(body != null ? body : "(빈 글)");

        return sb.toString();
    }

    /** CB-1: 같은 페르소나(botMembershipId)가 이미 댓글 달았는지 확인 (다른 페르소나는 허용) */
    private boolean hasSamePersonaComment(Long postId, Long botMembershipId) {
        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .anyMatch(c -> c.isAiGenerated() && c.getAuthor().getId().equals(botMembershipId));
    }

    // ── CB-1: 라이벌 체크 (같은 글에 다른 페르소나 댓글이 있으면 50% 확률로 반박) ──

    /**
     * 같은 스레드에 다른 페르소나가 답글을 달았는지 확인
     * @return 다른 페르소나의 답글 내용 (없으면 null)
     */
    private String findRivalThreadReply(Long threadId, BotPersona currentPersona) {
        List<ThreadReply> replies = threadReplyRepository.findByThreadIdOrderByCreatedAtAsc(
                threadId, PageRequest.of(0, 50)).getContent();
        for (ThreadReply r : replies) {
            if (r.isAiGenerated() && r.getAuthor() != null) {
                // 다른 봇 페르소나인지 확인 (studentId로 판별)
                String studentId = r.getAuthor().getStudentId();
                if (studentId != null && studentId.startsWith("BOT-") && !studentId.equals("BOT-" + currentPersona.name())) {
                    return r.getAuthor().getName() + ": " + r.getContent();
                }
            }
        }
        return null;
    }

    /**
     * 같은 게시글에 다른 페르소나가 댓글을 달았는지 확인
     */
    private String findRivalPostComment(Long postId, BotPersona currentPersona) {
        List<PostComment> comments = postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        for (PostComment c : comments) {
            if (c.isAiGenerated() && c.getAuthor() != null) {
                String studentId = c.getAuthor().getStudentId();
                if (studentId != null && studentId.startsWith("BOT-") && !studentId.equals("BOT-" + currentPersona.name())) {
                    return c.getAuthor().getName() + ": " + c.getContent();
                }
            }
        }
        return null;
    }

    /**
     * 같은 뉴스에 다른 페르소나가 댓글을 달았는지 확인
     */
    private String findRivalNewsComment(Long couplingId, BotPersona currentPersona) {
        List<NewsComment> comments = newsCommentRepository.findByCouplingIdOrderByCreatedAtAsc(couplingId);
        for (NewsComment c : comments) {
            if (c.isAiGenerated() && c.getAuthor() != null) {
                String studentId = c.getAuthor().getStudentId();
                if (studentId != null && studentId.startsWith("BOT-") && !studentId.equals("BOT-" + currentPersona.name())) {
                    return c.getAuthor().getName() + ": " + c.getContent();
                }
            }
        }
        return null;
    }

    /**
     * 현재 페르소나가 이미 해당 대상에 반박한 적 있는지 확인
     */
    private boolean hasAlreadyRebutted(Long targetId, Long botMembershipId, String targetType) {
        return switch (targetType) {
            case "thread" -> threadReplyRepository.findByThreadIdOrderByCreatedAtAsc(targetId, PageRequest.of(0, 50))
                    .getContent().stream().anyMatch(r -> r.getAuthor().getId().equals(botMembershipId));
            case "post" -> postCommentRepository.findByPostIdOrderByCreatedAtAsc(targetId).stream()
                    .anyMatch(c -> c.getAuthor().getId().equals(botMembershipId));
            case "news" -> newsCommentRepository.findByCouplingIdOrderByCreatedAtAsc(targetId).stream()
                    .anyMatch(c -> c.getAuthor().getId().equals(botMembershipId));
            default -> false;
        };
    }

    // ── 앵글 풀 (매 호출마다 랜덤 1개 주입 → 다양성 확보) ──

    private static final String[] BULLISH_ANGLES = {
            "실적 서프라이즈 가능성에 집중하라",
            "글로벌 경쟁사 대비 밸류에이션 할인에 집중하라",
            "산업 구조 변화가 이 회사에 유리한 이유에 집중하라",
            "기관/외국인 수급 시그널의 긍정적 측면에 집중하라",
            "경영진 전략 변화의 긍정적 신호에 집중하라",
            "시장이 과도하게 비관적인 이유를 반박하라",
            "이 회사의 숨겨진 자산(브랜드/특허/기술력)에 집중하라",
            "차트상 바닥 다지기 패턴이나 반등 신호에 집중하라",
            "동종업계 M&A나 구조조정이 가져올 수혜에 집중하라"
    };

    private static final String[] BEARISH_ANGLES = {
            "밸류에이션이 역사적 고점 대비 어디인지 지적하라",
            "경쟁사 진입이나 시장 포화 리스크에 집중하라",
            "매크로 환경(금리/환율/유동성) 악화 가능성에 집중하라",
            "내부자 매도나 수급 이탈 신호에 집중하라",
            "실적 기대치 하향 조정 가능성에 집중하라",
            "섹터 순환 관점에서 이미 피크인 근거에 집중하라",
            "규제/정책 변화 리스크에 집중하라",
            "기술적 저항선이나 차익실현 압력에 집중하라",
            "공급망 리스크나 원자재 가격 변동에 집중하라"
    };

    private static final String[] METAPHOR_DOMAINS = {
            "음식/요리", "스포츠/운동", "게임/e스포츠", "연애/소개팅",
            "날씨/계절", "여행/모험", "영화/드라마", "학교생활/시험",
            "자동차/레이싱", "동물/자연", "요리대회/맛집", "헬스/다이어트"
    };

    // ── 페르소나 프롬프트 (v3 — 사고과정 + 앵글풀) ──

    private String getPersonaSystemPrompt(BotPersona persona, String type) {
        String base = switch (persona) {
            case BULLISH -> "당신은 주식 투자 커뮤니티의 '황소'입니다.\n"
                    + "[배경] 위기 때 과감하게 매수해서 큰 수익을 낸 경험이 있다. '결국 오른다'가 신조.\n"
                    + "[사고방식] 뉴스를 보면 먼저 성장 기회를 찾는다. 악재를 보면 '이미 주가에 반영됐다'고 해석한다. "
                    + "밸류에이션을 볼 때 PER보다 성장률 대비 할인을 본다.\n"
                    + "[약점] 가끔 낙관이 지나칠 수 있다는 걸 본인도 안다. 그래서 '물론 ~리스크는 있지만'을 한 번 인정하고 넘어간다.\n"
                    + "[말투] 단정적 서술('~이다'), 비교급('A보다 B가 매력적'), 가끔 감탄사('이건 진짜').\n"
                    + "[이번 앵글] " + randomPick(BULLISH_ANGLES);
            case BEARISH -> "당신은 주식 투자 커뮤니티의 '불곰'입니다.\n"
                    + "[배경] 과거에 손절 타이밍을 놓겨 큰 손실을 본 적이 있다. 그래서 항상 '최악의 시나리오'부터 점검한다.\n"
                    + "[사고방식] 좋은 뉴스를 봐도 '이건 이미 주가에 반영된 거 아닌가?'를 먼저 의심한다. "
                    + "컨센서스와 다른 관점을 일부러 찾는다. 현금 확보의 가치를 중시한다.\n"
                    + "[약점] 가끔 좋은 기회를 놓치기도 한다. '기회일 수도 있지만 확인이 필요하다'며 인정할 때가 있다.\n"
                    + "[말투] 조건부('~할 수 있다', '~여부를 확인해야'), 질문형('~아닌가?'), '간과해선 안 된다'.\n"
                    + "[이번 앵글] " + randomPick(BEARISH_ANGLES);
            case METAPHOR -> "당신은 주식 투자 커뮤니티의 '물개'입니다.\n"
                    + "[배경] 주식을 어렵게 설명하는 게 싫은 사람. 복잡한 걸 한방에 이해시키는 비유의 달인.\n"
                    + "[사고방식] 복잡한 상황을 보면 '이거 뭐랑 비슷하지?'를 먼저 떠올린다. "
                    + "핵심을 꿰뚫는 비유 하나가 분석 열 줄보다 낫다고 믿는다.\n"
                    + "[약점] 가끔 비유가 너무 엉뚱해서 본인도 '아 이건 좀 억지인가?' 하고 웃는다.\n"
                    + "[말투] 반말 섞인 캐주얼, 감탄사 자주('ㅋㅋ', '오', '대박'), 짧고 임팩트 있게.\n"
                    + "[이번 비유 소재] " + randomPick(METAPHOR_DOMAINS) + " 도메인에서 비유를 가져오세요.";
        };

        String format = switch (type) {
            case "thread" -> "\n\n[형식] 트윗 스타일. 3~5줄로 짧게. 차트/표 없이 텍스트만. 시세 한줄 인용 포함. "
                    + "JSON 형식으로 응답: {\"title\": \"제목(50자 이내)\", \"content\": \"본문\"}";
            case "comment" -> "\n\n[형식] 댓글. 1~3줄로 짧고 간결하게. "
                    + "JSON 형식으로 응답: {\"content\": \"댓글 내용\"}";
            case "reply" -> "\n\n[형식] 답글. 상대의 말에 자연스럽게 반응하되 1~3줄로. "
                    + "JSON 형식으로 응답: {\"content\": \"답글 내용\"}";
            default -> "";
        };

        return base + format + "\n\n한국어로 작성하세요. 이모지는 1~2개 이내로 절제하세요. "
                + "응답 전에 잠깐 멈추고, 자신이 [" + persona.getDisplayName() + "]라는 것을 떠올려라. "
                + "[" + persona.getDisplayName() + "]라면 이 상황을 어떤 각도에서 볼 것인가?";
    }

    private String randomPick(String[] pool) {
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    // ── 반응형 응답 (AI 콘텐츠에 달린 사람 댓글에 응답) ──

    private Map<String, Object> handlePendingResponses(Long clubId, BotPersona persona, ClubMembership botMembership) {
        // 1. 스레드 답글 중 pending 확인
        List<ThreadReply> pendingThreadReplies = threadReplyRepository.findByNeedsAiResponseTrueAndThread_Club_Id(clubId);
        if (!pendingThreadReplies.isEmpty()) {
            return handlePendingThreadReply(pendingThreadReplies, clubId, persona, botMembership);
        }

        // 2. 게시판 댓글 중 pending 확인
        List<PostComment> pendingPostComments = postCommentRepository.findNeedsAiResponseByClubId(clubId);
        if (!pendingPostComments.isEmpty()) {
            return handlePendingPostComment(pendingPostComments, persona, botMembership);
        }

        // 3. 뉴스 댓글 중 pending 확인
        List<NewsComment> pendingNewsComments = newsCommentRepository.findByNeedsAiResponseTrue();
        if (!pendingNewsComments.isEmpty()) {
            return handlePendingNewsComment(pendingNewsComments, persona, botMembership);
        }

        return null; // pending 없음 → 정규 실행
    }

    /**
     * AI 스레드에 사람이 답글을 달면 → 첫 답글에 대댓글 1개, 같은 스레드 pending 전부 해제
     */
    private Map<String, Object> handlePendingThreadReply(List<ThreadReply> pending, Long clubId,
                                                          BotPersona persona, ClubMembership botMembership) {
        // 가장 오래된 것부터 처리, 같은 스레드끼리 그룹
        ThreadReply oldest = pending.get(0);
        StockThread thread = oldest.getThread();

        // 이미 AI가 이 스레드에 대댓글을 달았는지 확인
        boolean alreadyResponded = threadReplyRepository
                .findByThreadIdOrderByCreatedAtAsc(thread.getId(), PageRequest.of(0, 100))
                .getContent().stream()
                .anyMatch(r -> r.isAiGenerated() && r.getParentReply() != null);

        if (alreadyResponded) {
            // 이미 응답함 → 모든 pending 해제만
            pending.stream()
                    .filter(r -> r.getThread().getId().equals(thread.getId()))
                    .forEach(r -> { r.setNeedsAiResponse(false); threadReplyRepository.save(r); });
            return null; // 다음 pending으로 or 정규 실행
        }

        // 사람 답글들 컨텍스트 수집
        List<ThreadReply> humanReplies = pending.stream()
                .filter(r -> r.getThread().getId().equals(thread.getId()))
                .toList();

        StringBuilder context = new StringBuilder();
        context.append("[원글 - ").append(thread.getStockName()).append("]\n");
        context.append("제목: ").append(thread.getTitle()).append("\n");
        context.append("내용: ").append(thread.getContent()).append("\n\n");
        context.append("[사람들의 반응]\n");
        for (ThreadReply hr : humanReplies) {
            context.append("- ").append(hr.getAuthor().getName()).append(": ").append(hr.getContent()).append("\n");
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "reply");
        String userPrompt = context + "\n위 반응들에 대해 자연스럽게 답변하세요. "
                + "여러 반응을 한번에 아우르는 1~3줄 답글을 작성하세요. JSON으로 {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) return null;

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 2000) content = content.substring(0, 2000);

        // 첫 사람 답글에 대댓글로 작성
        ThreadReply aiReply = ThreadReply.builder()
                .thread(thread)
                .parentReply(oldest)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        threadReplyRepository.save(aiReply);

        thread.setReplyCount(thread.getReplyCount() + 1);
        threadRepository.save(thread);

        // 같은 스레드의 모든 pending 해제
        humanReplies.forEach(r -> { r.setNeedsAiResponse(false); threadReplyRepository.save(r); });

        log.info("AI 반응형 스레드 답글: threadId={}, persona={}", thread.getId(), persona.name());
        return Map.of("type", "REACTIVE_THREAD_REPLY", "content", content,
                "personaName", persona.getDisplayName(), "targetInfo", thread.getTitle());
    }

    /**
     * AI 게시판 댓글에 대댓글이 달리면 → 자기 본 댓글에 대댓글 1개, pending 전부 해제
     */
    private Map<String, Object> handlePendingPostComment(List<PostComment> pending,
                                                           BotPersona persona, ClubMembership botMembership) {
        PostComment oldest = pending.get(0);
        PostComment aiParent = oldest.getParentComment(); // AI의 원 댓글
        if (aiParent == null) return null;

        // 이미 응답했는지 확인
        boolean alreadyResponded = postCommentRepository.findByPostIdOrderByCreatedAtAsc(aiParent.getPost().getId())
                .stream()
                .anyMatch(c -> c.isAiGenerated() && c.getParentComment() != null
                        && c.getParentComment().getId().equals(aiParent.getId()));

        List<PostComment> sameParent = pending.stream()
                .filter(c -> c.getParentComment() != null && c.getParentComment().getId().equals(aiParent.getId()))
                .toList();

        if (alreadyResponded) {
            sameParent.forEach(c -> { c.setNeedsAiResponse(false); postCommentRepository.save(c); });
            return null;
        }

        StringBuilder context = new StringBuilder();
        context.append("[원 댓글] ").append(aiParent.getContent()).append("\n\n");
        context.append("[사람들의 반응]\n");
        for (PostComment hc : sameParent) {
            context.append("- ").append(hc.getAuthor().getName()).append(": ").append(hc.getContent()).append("\n");
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "reply");
        String userPrompt = context + "\n위 반응들에 자연스럽게 답변하세요. 1~3줄. JSON {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) return null;

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 500) content = content.substring(0, 500);

        PostComment aiReply = PostComment.builder()
                .post(aiParent.getPost())
                .parentComment(aiParent)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        postCommentRepository.save(aiReply);

        sameParent.forEach(c -> { c.setNeedsAiResponse(false); postCommentRepository.save(c); });

        log.info("AI 반응형 게시판 댓글: postId={}, persona={}", aiParent.getPost().getId(), persona.name());
        return Map.of("type", "REACTIVE_POST_COMMENT", "content", content,
                "personaName", persona.getDisplayName(), "targetInfo", aiParent.getPost().getTitle());
    }

    /**
     * AI 뉴스 댓글에 대댓글이 달리면 → 자기 본 댓글에 대댓글 1개, pending 전부 해제
     */
    private Map<String, Object> handlePendingNewsComment(List<NewsComment> pending,
                                                           BotPersona persona, ClubMembership botMembership) {
        NewsComment oldest = pending.get(0);
        NewsComment aiParent = oldest.getParentComment();
        if (aiParent == null) return null;

        boolean alreadyResponded = newsCommentRepository.findByCouplingIdOrderByCreatedAtAsc(aiParent.getCoupling().getId())
                .stream()
                .anyMatch(c -> c.isAiGenerated() && c.getParentComment() != null
                        && c.getParentComment().getId().equals(aiParent.getId()));

        List<NewsComment> sameParent = pending.stream()
                .filter(c -> c.getParentComment() != null && c.getParentComment().getId().equals(aiParent.getId()))
                .toList();

        if (alreadyResponded) {
            sameParent.forEach(c -> { c.setNeedsAiResponse(false); newsCommentRepository.save(c); });
            return null;
        }

        StringBuilder context = new StringBuilder();
        context.append("[원 댓글] ").append(aiParent.getContent()).append("\n\n");
        context.append("[사람들의 반응]\n");
        for (NewsComment hc : sameParent) {
            context.append("- ").append(hc.getAuthor().getName()).append(": ").append(hc.getContent()).append("\n");
        }

        String systemPrompt = getPersonaSystemPrompt(persona, "reply");
        String userPrompt = context + "\n위 반응들에 자연스럽게 답변하세요. 1~3줄. JSON {\"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, userPrompt, 1000);
        if (result == null) return null;

        String content = result.has("content") ? result.get("content").asText() : "";
        if (content.length() > 1000) content = content.substring(0, 1000);

        NewsComment aiReply = NewsComment.builder()
                .coupling(aiParent.getCoupling())
                .parentComment(aiParent)
                .author(botMembership)
                .content(content)
                .aiGenerated(true)
                .build();
        newsCommentRepository.save(aiReply);

        sameParent.forEach(c -> { c.setNeedsAiResponse(false); newsCommentRepository.save(c); });

        log.info("AI 반응형 뉴스 댓글: couplingId={}, persona={}", aiParent.getCoupling().getId(), persona.name());
        return Map.of("type", "REACTIVE_NEWS_COMMENT", "content", content,
                "personaName", persona.getDisplayName(), "targetInfo", "뉴스 댓글 반응");
    }

    // ── 가상 멤버십 관리 ──

    @Transactional
    public ClubMembership getOrCreateBotMembership(Long clubId, BotPersona persona) {
        String botName = persona.getEmoji() + " " + persona.getDisplayName();
        String botStudentId = "BOT-" + persona.name();

        List<ClubMembership> existing = membershipRepository.findByClubIdAndStatusOrderByNameAsc(
                clubId, MemberStatus.APPROVED);
        for (ClubMembership m : existing) {
            if (m.isBot() && botStudentId.equals(m.getStudentId())) {
                // 이름이 변경된 경우 업데이트
                if (!botName.equals(m.getName())) {
                    m.setName(botName);
                    membershipRepository.save(m);
                }
                return m;
            }
        }

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        ClubMembership botMembership = ClubMembership.builder()
                .user(null)
                .club(club)
                .name(botName)
                .studentId(botStudentId)
                .phone("BOT")
                .status(MemberStatus.APPROVED)
                .bot(true)
                .build();
        return membershipRepository.save(botMembership);
    }

    // ── 라운드로빈 ──

    private BotPersona nextPersona(Long clubId) {
        BotPersona[] personas = BotPersona.values();
        int idx = personaIndex.getOrDefault(clubId, 0);
        BotPersona selected = personas[idx % personas.length];
        personaIndex.put(clubId, idx + 1);
        return selected;
    }

    // ── 활성 동아리 목록 ──

    @Transactional(readOnly = true)
    public List<Long> getEnabledClubIds() {
        return settingsRepository.findAll().stream()
                .filter(ClubSettings::isAiCommentBotEnabled)
                .map(ClubSettings::getId)
                .collect(Collectors.toList());
    }

    // ── CB-7: 재조명글 (주간 리뷰) ──

    /**
     * 주 1회 실행 — 각 동아리의 AI 게시판에 재조명글 발행
     */
    @Transactional
    public Map<String, Object> executeWeeklyReview(Long clubId) {
        ClubSettings settings = settingsRepository.findById(clubId).orElse(null);
        if (settings == null || !settings.isAiCommentBotEnabled()) {
            return Map.of("skipped", true, "reason", "봇 비활성");
        }

        if (!cerebrasClient.isAvailable()) {
            return Map.of("skipped", true, "reason", "Cerebras 키 미설정");
        }

        // AI 게시판 찾기
        List<Board> aiBoards = boardRepository.findByClubIdAndBoardType(clubId, BoardType.AI);
        if (aiBoards.isEmpty()) {
            log.warn("AI 게시판 없음 (clubId={})", clubId);
            return Map.of("skipped", true, "reason", "AI 게시판 없음");
        }
        Board aiBoard = aiBoards.get(0);

        // 재조명 대상 선정: 최근 1주일 우선 → 부족하면 전체 백필
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        List<Post> candidates = postRepository.findFeaturedCandidatesRecent(clubId, oneWeekAgo);
        if (candidates.isEmpty()) {
            candidates = postRepository.findFeaturedCandidatesAll(clubId);
        }

        // 최대 5개에서 1개 선택
        if (candidates.size() > 5) {
            candidates = candidates.subList(0, 5);
        }

        if (candidates.isEmpty()) {
            log.info("재조명 대상 없음 (clubId={})", clubId);
            return Map.of("skipped", true, "reason", "재조명 대상 없음");
        }

        Post targetPost = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // 봇 멤버십 (BULLISH 기본 사용)
        ClubMembership botMembership = getOrCreateBotMembership(clubId, BotPersona.BULLISH);

        // 발행 이름
        String botName = settings.getWeeklyReviewBotName();
        if (botName == null || botName.isBlank()) {
            Club club = clubRepository.findById(clubId).orElse(null);
            botName = club != null ? club.getName() : "AI";
        }

        // Cerebras로 재조명글 생성
        String context = "재조명 대상 글:\n"
                + "제목: " + targetPost.getTitle() + "\n"
                + "작성자: " + targetPost.getAuthor().getName() + "\n"
                + "좋아요: " + targetPost.getLikeCount() + "개\n\n"
                + "[본문]\n" + (targetPost.getContent().length() > 3000
                    ? targetPost.getContent().substring(0, 3000) + "..." : targetPost.getContent());

        String systemPrompt = "당신은 투자 동아리의 AI 에디터입니다. "
                + "이번 주의 주목할 만한 글을 선정하여 재조명하는 칼럼을 작성합니다. "
                + "원글의 핵심 논점을 소개하고, 왜 이 글이 주목할 만한지 2~3문단으로 간결하게 설명하세요. "
                + "한국어로 작성하세요. JSON 형식: {\"title\": \"...\", \"content\": \"...\"}";

        JsonNode result = cerebrasClient.generateJson(systemPrompt, context, 2000);
        if (result == null) {
            return Map.of("error", "Cerebras 응답 없음");
        }

        String title = result.has("title") ? result.get("title").asText() : "이번 주의 주목 글";
        String content = result.has("content") ? result.get("content").asText() : "";
        if (title.length() > 200) title = title.substring(0, 200);

        // AI 게시판에 Post 생성 (원글의 팀 태깅 승계)
        Post reviewPost = Post.builder()
                .title(title)
                .content(content)
                .author(botMembership)
                .teams(new HashSet<>(targetPost.getTeams()))
                .aiGenerated(true)
                .build();
        reviewPost.getBoards().add(aiBoard);
        postRepository.save(reviewPost);

        // 원글 featuredAt 기록
        targetPost.setFeaturedAt(LocalDateTime.now());
        postRepository.save(targetPost);

        log.info("재조명글 발행: clubId={}, targetPostId={}, reviewPostId={}", clubId, targetPost.getId(), reviewPost.getId());

        return Map.of(
                "type", "WEEKLY_REVIEW",
                "title", title,
                "targetPostTitle", targetPost.getTitle(),
                "reviewPostId", reviewPost.getId()
        );
    }

    /** CB-5: 슈퍼어드민 설정에서 댓글봇 캡 min/max 조회 */
    @Transactional(readOnly = true)
    public int[] getCommentBotCapRange() {
        GlobalSettings gs = globalSettingsRepository.findById(1L).orElse(null);
        int min = gs != null ? gs.getDailyCommentBotMin() : 5;
        int max = gs != null ? gs.getDailyCommentBotMax() : 10;
        if (min > max) max = min;
        return new int[]{min, max};
    }
}
