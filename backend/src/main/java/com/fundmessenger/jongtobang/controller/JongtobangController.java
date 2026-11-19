package com.fundmessenger.jongtobang.controller;

import com.fundmessenger.jongtobang.dto.*;
import com.fundmessenger.jongtobang.service.JongtobangService;
import com.fundmessenger.jongtobang.service.StockChartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/jongtobang")
@RequiredArgsConstructor
public class JongtobangController {

    private final JongtobangService service;
    private final StockChartService chartService;

    // ── 종목 ──

    /** 종목 리스트 (sort: attention/gainers/losers/news/threads, search: 검색어) */
    @GetMapping("/stocks")
    public ResponseEntity<List<StockResponse>> getStocks(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "attention") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String marketGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getStocks(clubId, sort, search, market, marketGroup, page, size, userId));
    }

    /** 종목 상세 */
    @GetMapping("/stocks/{stockId}")
    public ResponseEntity<StockResponse> getStock(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getStock(clubId, stockId, userId));
    }

    /** 종목 차트 데이터 (period: daily/weekly/monthly) */
    @GetMapping("/stocks/{stockId}/chart")
    public ResponseEntity<StockChartResponse> getChart(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "daily") String period,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 멤버 검증만 (Stock은 글로벌)
        service.getStock(clubId, stockId, userId);
        return ResponseEntity.ok(chartService.getChart(stockId, period));
    }

    // ── 뉴스 ──

    /** 종목별 뉴스 리스트 (hype score 내림차순) */
    @GetMapping("/stocks/{stockId}/news")
    public ResponseEntity<List<NewsResponse>> getNews(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getNews(clubId, stockId, page, size, userId));
    }

    /** 뉴스 상세 (본문 + 원문) */
    @GetMapping("/stocks/{stockId}/news/{couplingId}")
    public ResponseEntity<NewsResponse> getNewsDetail(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long couplingId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getNewsDetail(clubId, stockId, couplingId, userId));
    }

    // ── 뉴스 댓글 ──

    /** 뉴스 댓글 목록 */
    @GetMapping("/stocks/{stockId}/news/{couplingId}/comments")
    public ResponseEntity<List<NewsCommentResponse>> getNewsComments(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long couplingId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getNewsComments(clubId, stockId, couplingId, userId));
    }

    /** 뉴스 댓글 작성 */
    @PostMapping("/stocks/{stockId}/news/{couplingId}/comments")
    public ResponseEntity<NewsCommentResponse> createNewsComment(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long couplingId,
            @Valid @RequestBody CreateNewsCommentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.createNewsComment(clubId, stockId, couplingId, request, userId));
    }

    /** 뉴스 댓글 삭제 */
    @DeleteMapping("/stocks/{stockId}/news/{couplingId}/comments/{commentId}")
    public ResponseEntity<Void> deleteNewsComment(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long couplingId,
            @PathVariable Long commentId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        service.deleteNewsComment(clubId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── 스레드 ──

    /** 스레드 목록 (sort: latest/popular) */
    @GetMapping("/stocks/{stockId}/threads")
    public ResponseEntity<Page<StockThreadResponse>> getThreads(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getThreads(clubId, stockId, sort, page, size, userId));
    }

    /** 스레드 작성 */
    @PostMapping("/stocks/{stockId}/threads")
    public ResponseEntity<StockThreadResponse> createThread(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @Valid @RequestBody CreateThreadRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.createThread(clubId, stockId, request, userId));
    }

    /** 스레드 삭제 */
    @DeleteMapping("/stocks/{stockId}/threads/{threadId}")
    public ResponseEntity<Void> deleteThread(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long threadId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        service.deleteThread(clubId, threadId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── 답글 ──

    /** 답글 목록 */
    @GetMapping("/stocks/{stockId}/threads/{threadId}/replies")
    public ResponseEntity<Page<ThreadReplyResponse>> getReplies(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long threadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.getReplies(clubId, threadId, page, size, userId));
    }

    /** 답글 작성 */
    @PostMapping("/stocks/{stockId}/threads/{threadId}/replies")
    public ResponseEntity<ThreadReplyResponse> createReply(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long threadId,
            @Valid @RequestBody CreateReplyRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.createReply(clubId, threadId, request.getContent(), request.getParentReplyId(), userId));
    }

    /** 답글 삭제 */
    @DeleteMapping("/stocks/{stockId}/threads/{threadId}/replies/{replyId}")
    public ResponseEntity<Void> deleteReply(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long threadId,
            @PathVariable Long replyId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        service.deleteReply(clubId, threadId, replyId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── 공감 ──

    /** 공감 토글 (POST = toggle) */
    @PostMapping("/stocks/{stockId}/threads/{threadId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long threadId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.toggleLike(clubId, threadId, userId));
    }

    /** 뉴스 공감 토글 */
    @PostMapping("/stocks/{stockId}/news/{couplingId}/like")
    public ResponseEntity<Map<String, Object>> toggleNewsLike(
            @PathVariable Long clubId,
            @PathVariable Long stockId,
            @PathVariable Long couplingId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.toggleNewsLike(clubId, stockId, couplingId, userId));
    }
}
