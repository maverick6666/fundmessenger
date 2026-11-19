package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.fund.dto.AiDecisionRequest;
import com.fundmessenger.fund.dto.AiDecisionResponse;
import com.fundmessenger.fund.service.AiDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}/ai")
@RequiredArgsConstructor
public class AiDecisionController {

    private final AiDecisionService aiDecisionService;
    private final ClubAuthorizationService authService;

    /**
     * AI 의사결정서 생성
     * POST /api/clubs/{clubId}/fund/teams/{teamId}/ai/decision-note
     * 매니저/팀장 권한 필요
     */
    @PostMapping("/decision-note")
    public ResponseEntity<AiDecisionResponse> generateDecisionNote(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid AiDecisionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        AiDecisionResponse response = aiDecisionService.generateDecisionNote(
                clubId, teamId, request.getChatRoomId(), membership);

        return ResponseEntity.ok(response);
    }
}
