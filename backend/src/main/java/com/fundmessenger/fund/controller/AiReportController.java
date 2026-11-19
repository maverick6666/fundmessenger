package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.fund.dto.GenerateReportRequest;
import com.fundmessenger.fund.dto.GenerateReportResponse;
import com.fundmessenger.fund.service.AiReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}/ai")
@RequiredArgsConstructor
public class AiReportController {

    private final AiReportService aiReportService;
    private final ClubAuthorizationService authService;

    /** 운용보고서 AI 생성 (매니저/팀장) */
    @PostMapping("/operation-report")
    public ResponseEntity<GenerateReportResponse> generateOperationReport(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid GenerateReportRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        GenerateReportResponse response = aiReportService.generateOperationReport(
                clubId, teamId, request, membership);
        return ResponseEntity.ok(response);
    }
}
