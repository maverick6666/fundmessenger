package com.fundmessenger.jongtobang.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.InputSanitizer;
import com.fundmessenger.jongtobang.dto.*;
import com.fundmessenger.jongtobang.entity.*;
import com.fundmessenger.jongtobang.repository.*;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JongtobangService {

    private final StockRepository stockRepository;
    private final StockNewsCouplingRepository couplingRepository;
    private final NewsCommentRepository commentRepository;
    private final StockThreadRepository threadRepository;
    private final ThreadReplyRepository replyRepository;
    private final ThreadLikeRepository likeRepository;
    private final NewsLikeRepository newsLikeRepository;
    private final ClubAuthorizationService authService;
    private final StockPriceService stockPriceService;
    private final StockRankingCacheService stockRankingCacheService;
    private final ApplicationEventPublisher eventPublisher;

    // ── 종목 목록 (글로벌) ──

    @Transactional
    public List<StockResponse> getStocks(Long clubId, String sort, String search, String market, String marketGroup,
                                          int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);

        boolean hasMarket = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);
        List<String> groupedMarkets = resolveGroupedMarkets(marketGroup);
        String sortKey = sort != null ? sort : "attention";

        // 검색 모드
        if (search != null && !search.isBlank()) {
            Pageable pageable = PageRequest.of(page, size);
            List<Stock> stocks = hasMarket
                    ? stockRepository.searchActiveByMarket(market, search.trim(), pageable)
                    : !groupedMarkets.isEmpty()
                    ? stockRepository.searchActiveByMarkets(groupedMarkets, search.trim(), pageable)
                    : stockRepository.searchActive(search.trim(), pageable);
            return toStockResponseBatch(stocks, clubId);
        }

        // 변동률 정렬: KIS 실시간 API 직접 호출 (페이지네이션 없음, 최대 30건)
        if ("gainers".equals(sortKey) || "losers".equals(sortKey)) {
            List<StockResponse> ranked = stockRankingCacheService.getSnapshot(sortKey, market, marketGroup, size);
            return withCounts(ranked, clubId);
        }

        if ("volume".equals(sortKey)) {
            return withCounts(stockRankingCacheService.getSnapshot(sortKey, market, marketGroup, size), clubId);
        }

        if ("marketCap".equals(sortKey)) {
            return withCounts(stockRankingCacheService.getSnapshot(sortKey, market, marketGroup, size), clubId);
        }

        // 주목도 정렬: DB에서 attention_score 기준 조회
        Pageable pageable = PageRequest.of(page, size);
        List<Stock> stocks = hasMarket
                ? stockRepository.findByAttentionAndMarket(market, pageable)
                : !groupedMarkets.isEmpty()
                ? stockRepository.findByAttentionAndMarkets(groupedMarkets, pageable)
                : stockRepository.findByAttention(pageable);
        return toStockResponseBatch(stocks, clubId);
    }

    private List<String> resolveGroupedMarkets(String marketGroup) {
        if ("DOMESTIC".equalsIgnoreCase(marketGroup)) {
            return List.of("KOSPI", "KOSDAQ");
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return List.of("NASDAQ", "NYSE", "AMEX");
        }
        return List.of();
    }

    private List<Stock> resolveVolumeStocks(boolean hasMarket, String market, String marketGroup, int size) {
        if (hasMarket && ("KOSPI".equals(market) || "KOSDAQ".equals(market))) {
            return stockPriceService.getDomesticVolumeRankingByMarket(market, size);
        }
        if (hasMarket && ("NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market))) {
            return stockPriceService.getOverseasVolumeRanking(market, size);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return stockPriceService.getCombinedOverseasVolumeRanking(size);
        }
        return stockPriceService.getCombinedDomesticVolumeRanking(size);
    }

    private List<Stock> resolveMarketCapStocks(boolean hasMarket, String market, String marketGroup, int size) {
        if (hasMarket && ("KOSPI".equals(market) || "KOSDAQ".equals(market))) {
            return stockPriceService.getDomesticMarketCapRankingByMarket(market, size);
        }
        if (hasMarket && ("NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market))) {
            return stockPriceService.getOverseasMarketCapRanking(market, size);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return stockPriceService.getCombinedOverseasMarketCapRanking(size);
        }
        return stockPriceService.getCombinedDomesticMarketCapRanking(size);
    }

    /** 단건 변환 (단일 종목 상세 화면에서 사용) — SEC-1: clubId 스코프 */
    private StockResponse toStockResponse(Stock stock, Long clubId) {
        long newsCount = couplingRepository.countByStockId(stock.getId());
        long threadCount = threadRepository.countByStockIdAndClubId(stock.getId(), clubId);
        return StockResponse.from(stock, newsCount, threadCount);
    }

    /** 배치 변환: 뉴스/쓰레드 카운트를 한 번에 조회 (N+1 방지) — SEC-1: clubId 스코프 */
    private List<StockResponse> toStockResponseBatch(List<Stock> stocks, Long clubId) {
        if (stocks.isEmpty()) return List.of();

        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();

        Map<Long, Long> newsCountMap = couplingRepository.countByStockIds(stockIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Map<Long, Long> threadCountMap = threadRepository.countByStockIdsAndClubId(stockIds, clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Set<Long> newNewsStockIds = new HashSet<>(couplingRepository.findStockIdsWithNewNews(
                java.time.LocalDateTime.now().minusHours(1)));

        return stocks.stream()
                .map(s -> StockResponse.from(s,
                        newsCountMap.getOrDefault(s.getId(), 0L),
                        threadCountMap.getOrDefault(s.getId(), 0L))
                        .toBuilder().hasNewNews(newNewsStockIds.contains(s.getId())).build())
                .collect(Collectors.toList());
    }

    private List<StockResponse> withCounts(List<StockResponse> stocks, Long clubId) {
        if (stocks.isEmpty()) return List.of();

        List<Long> stockIds = stocks.stream()
                .map(StockResponse::getId)
                .filter(Objects::nonNull)
                .toList();

        if (stockIds.isEmpty()) return stocks;

        Map<Long, Long> newsCountMap = couplingRepository.countByStockIds(stockIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Map<Long, Long> threadCountMap = threadRepository.countByStockIdsAndClubId(stockIds, clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        Set<Long> newNewsStockIds = new HashSet<>(couplingRepository.findStockIdsWithNewNews(
                java.time.LocalDateTime.now().minusHours(1)));

        return stocks.stream()
                .map(stock -> stock.toBuilder()
                        .newsCount(newsCountMap.getOrDefault(stock.getId(), 0L))
                        .threadCount(threadCountMap.getOrDefault(stock.getId(), 0L))
                        .hasNewNews(stock.getId() != null && newNewsStockIds.contains(stock.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public StockResponse getStock(Long clubId, Long stockId, Long userId) {
        authService.validateClubMember(userId, clubId);
        Stock stock = getStockOrThrow(stockId);

        // on-demand 실세 갱신 (5분 이내 갱신 이력 없으면)
        stockPriceService.refreshPriceOnDemand(stock);

        return toStockResponse(stock, clubId);
    }

    // ── 뉴스 ──

    @Transactional(readOnly = true)
    public List<NewsResponse> getNews(Long clubId, Long stockId, int page, int size, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        getStockOrThrow(stockId);
        List<StockNewsCoupling> couplings = couplingRepository.findByStockIdSentimentFirst(
                stockId, PageRequest.of(page, size));
        return couplings.stream().map(c -> {
            boolean liked = newsLikeRepository.existsByCouplingIdAndMemberId(c.getId(), membership.getId());
            return NewsResponse.fromSummary(c, liked);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NewsResponse getNewsDetail(Long clubId, Long stockId, Long couplingId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockNewsCoupling coupling = couplingRepository.findById(couplingId)
                .orElseThrow(() -> new NotFoundException("뉴스를 찾을 수 없습니다: " + couplingId));
        if (!coupling.getStock().getId().equals(stockId)) {
            throw new BadRequestException("해당 종목의 뉴스가 아닙니다");
        }
        boolean liked = newsLikeRepository.existsByCouplingIdAndMemberId(couplingId, membership.getId());
        return NewsResponse.fromDetail(coupling, liked);
    }

    // ── 뉴스 댓글 ──

    @Transactional(readOnly = true)
    public List<NewsCommentResponse> getNewsComments(Long clubId, Long stockId, Long couplingId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return commentRepository.findByCouplingIdOrderByCreatedAtAsc(couplingId).stream()
                .map(NewsCommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public NewsCommentResponse createNewsComment(Long clubId, Long stockId, Long couplingId,
                                                  CreateNewsCommentRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockNewsCoupling coupling = couplingRepository.findById(couplingId)
                .orElseThrow(() -> new NotFoundException("뉴스를 찾을 수 없습니다: " + couplingId));

        // SEC-5: coupling이 해당 종목에 속하는지 검증
        if (!coupling.getStock().getId().equals(stockId)) {
            throw new BadRequestException("해당 종목의 뉴스가 아닙니다");
        }

        NewsComment parentComment = null;
        boolean needsAiResponse = false;

        if (request.getParentCommentId() != null) {
            parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new NotFoundException("부모 댓글을 찾을 수 없습니다: " + request.getParentCommentId()));
            if (parentComment.getParentComment() != null) {
                throw new BadRequestException("답글은 1단계까지만 가능합니다 (depth-1 제한)");
            }
            if (parentComment.isAiGenerated()) {
                needsAiResponse = true;
            }
        }

        NewsComment comment = NewsComment.builder()
                .coupling(coupling)
                .parentComment(parentComment)
                .author(membership)
                .content(InputSanitizer.sanitizePlainText(request.getContent()))
                .needsAiResponse(needsAiResponse)
                .build();
        NewsComment saved = commentRepository.save(comment);

        couplingRepository.incrementCommentCount(couplingId);

        log.info("뉴스 댓글 작성: couplingId={}, commentId={}, parentId={}", couplingId, saved.getId(), request.getParentCommentId());

        // 알림: 대댓글이면 부모 댓글 작성자에게
        if (parentComment != null) {
            Long parentAuthorUserId = parentComment.getAuthor().getUser().getId();
            if (!parentAuthorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, parentAuthorUserId, NotificationType.COMMENT_REPLY,
                        membership.getName() + " replied to your comment.",
                        request.getContent().length() > 50 ? request.getContent().substring(0, 50) + "..." : request.getContent(),
                        "/club/jongtobang/" + stockId
                ));
            }
        }

        return NewsCommentResponse.from(saved);
    }

    @Transactional
    public void deleteNewsComment(Long clubId, Long commentId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        NewsComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // SEC-4: 같은 동아리의 댓글만 삭제 방지
        if (!comment.getAuthor().getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 댓글이 아닙니다");
        }

        boolean isAuthor = comment.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("댓글 삭제 권한이 없습니다");
        }

        couplingRepository.decrementCommentCount(comment.getCoupling().getId());

        commentRepository.delete(comment);
        log.info("뉴스 댓글 삭제: commentId={}", commentId);
    }

    // ── 쓰레드 ──

    @Transactional(readOnly = true)
    public Page<StockThreadResponse> getThreads(Long clubId, Long stockId, String sort, int page, int size, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        getStockOrThrow(stockId);

        Pageable pageable = PageRequest.of(page, size);
        Page<StockThread> threads;

        if ("popular".equals(sort)) {
            threads = threadRepository.findByStockIdAndClubIdOrderByPopularity(stockId, clubId, pageable);
        } else {
            threads = threadRepository.findByStockIdAndClubIdOrderByCreatedAtDesc(stockId, clubId, pageable);
        }

        return threads.map(t -> {
            boolean liked = likeRepository.existsByThreadIdAndMemberId(t.getId(), membership.getId());
            return StockThreadResponse.from(t, liked);
        });
    }

    @Transactional
    public StockThreadResponse createThread(Long clubId, Long stockId, CreateThreadRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Stock stock = getStockOrThrow(stockId);

        StockThread thread = StockThread.builder()
                .club(membership.getClub())
                .stock(stock)
                .author(membership)
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .market(stock.getMarket())
                .title(deriveThreadTitle(request))
                .content(InputSanitizer.sanitizePlainText(request.getContent()))
                .build();

        StockThread saved = threadRepository.save(thread);
        log.info("종토방 쓰레드 작성: clubId={}, stockId={}, threadId={}", clubId, stockId, saved.getId());
        return StockThreadResponse.from(saved, false);
    }

    /** title 미입력 시 content 첫 줄에서 자동 생성 (최대 50자) */
    private String deriveThreadTitle(CreateThreadRequest request) {
        String title = request.getTitle();
        if (title != null && !title.isBlank()) {
            return InputSanitizer.sanitizePlainText(title);
        }
        String content = request.getContent().trim();
        String firstLine = content.contains("\n") ? content.substring(0, content.indexOf('\n')).trim() : content;
        if (firstLine.length() > 50) firstLine = firstLine.substring(0, 50) + "…";
        return InputSanitizer.sanitizePlainText(firstLine);
    }

    @Transactional
    public void deleteThread(Long clubId, Long threadId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockThread thread = getThreadOrThrow(threadId, clubId);

        boolean isAuthor = thread.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("쓰레드 삭제 권한이 없습니다");
        }

        // SEC-8: 벌크 삭제 (기존: N+1 루프 + 본인 좋아요만 삭제 → 전체 삭제로 교체)
        replyRepository.deleteAllByThreadId(threadId);
        likeRepository.deleteAllByThreadId(threadId);
        threadRepository.delete(thread);
        log.info("종토방 쓰레드 삭제: clubId={}, threadId={}", clubId, threadId);
    }

    // ── 답글 ──

    @Transactional(readOnly = true)
    public Page<ThreadReplyResponse> getReplies(Long clubId, Long threadId, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);
        getThreadOrThrow(threadId, clubId);
        return replyRepository.findByThreadIdOrderByCreatedAtAsc(threadId, PageRequest.of(page, size))
                .map(ThreadReplyResponse::from);
    }

    @Transactional
    public ThreadReplyResponse createReply(Long clubId, Long threadId, String content, Long parentReplyId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockThread thread = getThreadOrThrow(threadId, clubId);

        ThreadReply parentReply = null;
        boolean needsAiResponse = false;

        if (parentReplyId != null) {
            parentReply = replyRepository.findById(parentReplyId)
                    .orElseThrow(() -> new NotFoundException("부모 답글을 찾을 수 없습니다: " + parentReplyId));
            if (parentReply.getParentReply() != null) {
                throw new BadRequestException("답글은 1단계까지만 가능합니다 (depth-1 제한)");
            }
            // 부모 답글이 AI 생성이면 AI 응답 필요
            if (parentReply.isAiGenerated()) {
                needsAiResponse = true;
            }
        } else if (thread.isAiGenerated()) {
            // AI 쓰레드에 직접 답글 → AI 응답 필요
            needsAiResponse = true;
        }

        ThreadReply reply = ThreadReply.builder()
                .thread(thread)
                .parentReply(parentReply)
                .author(membership)
                .content(InputSanitizer.sanitizePlainText(content))
                .needsAiResponse(needsAiResponse)
                .build();

        ThreadReply saved = replyRepository.save(reply);

        threadRepository.incrementReplyCount(threadId);

        log.info("종토방 답글 작성: threadId={}, replyId={}, parentReplyId={}", threadId, saved.getId(), parentReplyId);

        // 알림: 대댓글이면 부모 답글 작성자에게, 아니면 쓰레드 작성자에게
        if (parentReply != null) {
            Long parentAuthorUserId = parentReply.getAuthor().getUser().getId();
            if (!parentAuthorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, parentAuthorUserId, NotificationType.COMMENT_REPLY,
                        membership.getName() + " replied to your reply.",
                        content.length() > 50 ? content.substring(0, 50) + "..." : content,
                        "/club/jongtobang/" + thread.getStock().getId()
                ));
            }
        } else {
            Long threadAuthorUserId = thread.getAuthor().getUser().getId();
            if (!threadAuthorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, threadAuthorUserId, NotificationType.POST_COMMENT,
                        membership.getName() + " commented on your thread.",
                        content.length() > 50 ? content.substring(0, 50) + "..." : content,
                        "/club/jongtobang/" + thread.getStock().getId()
                ));
            }
        }

        return ThreadReplyResponse.from(saved);
    }

    @Transactional
    public void deleteReply(Long clubId, Long threadId, Long replyId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        getThreadOrThrow(threadId, clubId);

        ThreadReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new NotFoundException("답글을 찾을 수 없습니다: " + replyId));
        if (!reply.getThread().getId().equals(threadId)) {
            throw new BadRequestException("해당 쓰레드의 답글이 아닙니다");
        }

        boolean isAuthor = reply.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("답글 삭제 권한이 없습니다");
        }

        replyRepository.delete(reply);

        threadRepository.decrementReplyCount(threadId);

        log.info("종토방 답글 삭제: threadId={}, replyId={}", threadId, replyId);
    }

    // ── 공감 토글 ──

    @Transactional
    public Map<String, Object> toggleLike(Long clubId, Long threadId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockThread thread = getThreadOrThrow(threadId, clubId);

        Optional<ThreadLike> existing = likeRepository.findByThreadIdAndMemberId(threadId, membership.getId());
        boolean liked;

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            threadRepository.decrementLikeCount(threadId);
            liked = false;
        } else {
            ThreadLike like = ThreadLike.builder()
                    .thread(thread)
                    .member(membership)
                    .build();
            likeRepository.save(like);
            threadRepository.incrementLikeCount(threadId);
            liked = true;
        }

        // 알림: 좋아요 (취소 시에는 보내지 않음)
        if (liked) {
            Long threadAuthorUserId = thread.getAuthor().getUser().getId();
            if (!threadAuthorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, threadAuthorUserId, NotificationType.CONTENT_LIKED,
                        membership.getName() + "님이 쓰레드를 추천했습니다",
                        thread.getTitle(),
                        "/club/jongtobang/" + thread.getStock().getId()
                ));
            }
        }

        // Re-read to get the atomically updated count
        int updatedCount = threadRepository.findById(threadId)
                .map(StockThread::getLikeCount).orElse(0);
        return Map.of("liked", liked, "likeCount", updatedCount);
    }

    // ── 뉴스 공감 토글 ──

    @Transactional
    public Map<String, Object> toggleNewsLike(Long clubId, Long stockId, Long couplingId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        StockNewsCoupling coupling = couplingRepository.findById(couplingId)
                .orElseThrow(() -> new NotFoundException("뉴스를 찾을 수 없습니다: " + couplingId));
        if (!coupling.getStock().getId().equals(stockId)) {
            throw new BadRequestException("해당 종목의 뉴스가 아닙니다");
        }

        Optional<NewsLike> existing = newsLikeRepository.findByCouplingIdAndMemberId(couplingId, membership.getId());
        boolean liked;

        if (existing.isPresent()) {
            newsLikeRepository.delete(existing.get());
            couplingRepository.decrementLikeCount(couplingId);
            liked = false;
        } else {
            NewsLike like = NewsLike.builder()
                    .coupling(coupling)
                    .member(membership)
                    .build();
            newsLikeRepository.save(like);
            couplingRepository.incrementLikeCount(couplingId);
            liked = true;
        }

        // Re-read to get the atomically updated count
        int updatedCount = couplingRepository.findById(couplingId)
                .map(StockNewsCoupling::getLikeCount).orElse(0);
        return Map.of("liked", liked, "likeCount", updatedCount);
    }

    // ── 헬퍼 ──

    private Stock getStockOrThrow(Long stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new NotFoundException("종목을 찾을 수 없습니다: " + stockId));
    }

    private StockThread getThreadOrThrow(Long threadId, Long clubId) {
        StockThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new NotFoundException("해당 쓰레드를 찾을 수 없습니다: " + threadId));
        if (!thread.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 쓰레드가 아닙니다");
        }
        return thread;
    }
}
