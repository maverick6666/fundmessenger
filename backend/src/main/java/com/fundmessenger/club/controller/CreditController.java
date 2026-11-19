package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.AllocateCreditRequest;
import com.fundmessenger.club.dto.CreditHistoryResponse;
import com.fundmessenger.club.dto.CreditSummaryResponse;
import com.fundmessenger.club.service.CreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /** 크레딧 요약 (동아리 잔액 + 팀별 잔액) */
    @GetMapping("/summary")
    public ResponseEntity<CreditSummaryResponse> getSummary(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(creditService.getCreditSummary(clubId, userId));
    }

    /** 크레딧 내역 */
    @GetMapping("/history")
    public ResponseEntity<List<CreditHistoryResponse>> getHistory(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(creditService.getCreditHistory(clubId, userId));
    }

    /** 팀에 분배 (회장/VP) */
    @PostMapping("/allocate")
    public ResponseEntity<CreditSummaryResponse> allocate(
            @PathVariable Long clubId,
            @Valid @RequestBody AllocateCreditRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(creditService.allocateToTeam(clubId, request, userId));
    }

    /** 팀에서 회수 (회장/VP) */
    @PostMapping("/reclaim")
    public ResponseEntity<CreditSummaryResponse> reclaim(
            @PathVariable Long clubId,
            @Valid @RequestBody AllocateCreditRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(creditService.reclaimFromTeam(clubId, request, userId));
    }
}
