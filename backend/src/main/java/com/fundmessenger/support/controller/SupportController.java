package com.fundmessenger.support.controller;

import com.fundmessenger.support.dto.*;
import com.fundmessenger.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /** 문의 생성 (모든 로그인 사용자) */
    @PostMapping("/tickets")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.createTicket(userId, request));
    }

    /** 내 문의 목록 */
    @GetMapping("/tickets")
    public ResponseEntity<List<TicketResponse>> getMyTickets(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.getMyTickets(userId));
    }

    /** 내 문의 상세 (답변 포함) */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketDetailResponse> getMyTicketDetail(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.getMyTicketDetail(id, userId));
    }

    /** 사용자 추가 답변 */
    @PostMapping("/tickets/{id}/reply")
    public ResponseEntity<ReplyResponse> createUserReply(
            @PathVariable Long id,
            @Valid @RequestBody CreateReplyRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.createUserReply(id, request, userId));
    }
}
