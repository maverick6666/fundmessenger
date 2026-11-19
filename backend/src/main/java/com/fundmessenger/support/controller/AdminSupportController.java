package com.fundmessenger.support.controller;

import com.fundmessenger.support.dto.*;
import com.fundmessenger.support.entity.TicketStatus;
import com.fundmessenger.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService supportService;

    /** 전체 티켓 목록 (상태 필터 선택) */
    @GetMapping("/tickets")
    public ResponseEntity<List<TicketResponse>> getAllTickets(
            @RequestParam(required = false) String status,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        if (status != null && !status.isBlank()) {
            TicketStatus ticketStatus = TicketStatus.valueOf(status);
            return ResponseEntity.ok(supportService.getTicketsByStatus(ticketStatus, userId));
        }
        return ResponseEntity.ok(supportService.getAllTickets(userId));
    }

    /** 티켓 상세 + 답변 목록 */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketDetailResponse> getTicketDetail(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.getTicketDetail(id, userId));
    }

    /** 답변 작성 */
    @PostMapping("/tickets/{id}/reply")
    public ResponseEntity<ReplyResponse> createReply(
            @PathVariable Long id,
            @Valid @RequestBody CreateReplyRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.createAdminReply(id, request, userId));
    }

    /** 티켓 종료 */
    @PostMapping("/tickets/{id}/close")
    public ResponseEntity<TicketResponse> closeTicket(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(supportService.closeTicket(id, userId));
    }
}
