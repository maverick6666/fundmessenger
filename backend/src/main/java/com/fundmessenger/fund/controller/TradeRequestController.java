package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.fund.dto.*;
import com.fundmessenger.fund.entity.RequestStatus;
import com.fundmessenger.fund.entity.TradeRequest;
import com.fundmessenger.fund.service.TradeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}/requests")
@RequiredArgsConstructor
public class TradeRequestController {

    private final TradeRequestService tradeRequestService;
    private final ClubAuthorizationService authService;
    private final MemberTagRepository memberTagRepository;

    // ========== 요청 생성 ==========

    /** 매수 요청 (팀원) */
    @PostMapping("/buy")
    public ResponseEntity<TradeRequestResponse> createBuyRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreateBuyRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = validateTeamMember(userId, clubId, teamId);

        TradeRequest saved = tradeRequestService.createBuyRequest(teamId, request, membership);
        return ResponseEntity.ok(TradeRequestResponse.from(saved));
    }

    /** 매도 요청 (팀원) */
    @PostMapping("/sell")
    public ResponseEntity<TradeRequestResponse> createSellRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreateSellRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = validateTeamMember(userId, clubId, teamId);

        TradeRequest saved = tradeRequestService.createSellRequest(teamId, request, membership);
        return ResponseEntity.ok(TradeRequestResponse.from(saved));
    }

    // ========== 조회 ==========

    /** 요청 목록 (status 필터) */
    @GetMapping
    public ResponseEntity<List<TradeRequestResponse>> getRequests(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        RequestStatus requestStatus = status != null ? RequestStatus.valueOf(status.toUpperCase()) : null;
        List<TradeRequestResponse> responses = tradeRequestService.getRequests(teamId, requestStatus);
        return ResponseEntity.ok(responses);
    }

    /** 내 요청만 */
    @GetMapping("/mine")
    public ResponseEntity<List<TradeRequestResponse>> getMyRequests(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = validateTeamMember(userId, clubId, teamId);

        List<TradeRequestResponse> responses = tradeRequestService.getMyRequests(teamId, membership.getId());
        return ResponseEntity.ok(responses);
    }

    /** 요청 상세 */
    @GetMapping("/{requestId}")
    public ResponseEntity<TradeRequestResponse> getRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long requestId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        TradeRequest request = tradeRequestService.getRequestById(requestId);
        validateRequestBelongsToTeam(request, teamId);
        return ResponseEntity.ok(TradeRequestResponse.from(request));
    }

    // ========== 매니저/팀장 액션 ==========

    /** 승인 */
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<TradeRequestResponse> approveRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveRequest approveData,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        TradeRequest request = tradeRequestService.getRequestById(requestId);
        validateRequestBelongsToTeam(request, teamId);

        TradeRequest approved = tradeRequestService.approveRequest(requestId, approveData, membership);
        return ResponseEntity.ok(TradeRequestResponse.from(approved));
    }

    /** 거절 */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<TradeRequestResponse> rejectRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long requestId,
            @RequestBody @Valid RejectRequest rejectData,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        TradeRequest request = tradeRequestService.getRequestById(requestId);
        validateRequestBelongsToTeam(request, teamId);

        TradeRequest rejected = tradeRequestService.rejectRequest(requestId, rejectData.getRejectionReason(), membership);
        return ResponseEntity.ok(TradeRequestResponse.from(rejected));
    }

    /** 토론 시작 */
    @PostMapping("/{requestId}/discuss")
    public ResponseEntity<TradeRequestResponse> startDiscussion(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long requestId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        TradeRequest request = tradeRequestService.getRequestById(requestId);
        validateRequestBelongsToTeam(request, teamId);

        TradeRequest discussed = tradeRequestService.startDiscussion(requestId);
        return ResponseEntity.ok(TradeRequestResponse.from(discussed));
    }

    /** 삭제 (매니저) */
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> deleteRequest(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long requestId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        TradeRequest request = tradeRequestService.getRequestById(requestId);
        validateRequestBelongsToTeam(request, teamId);

        tradeRequestService.deleteRequest(requestId);
        return ResponseEntity.noContent().build();
    }

    // ========== Helpers ==========

    private ClubMembership validateTeamMember(Long userId, Long clubId, Long teamId) {
        ClubMembership membership = authService.validateActiveMember(userId, clubId);
        // 임원급은 모든 팀 접근 가능
        if (authService.isExecutive(membership)) return membership;
        // 팀원인지 확인
        if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId)) {
            throw new SecurityException("해당 팀의 멤버가 아닙니다");
        }
        return membership;
    }

    private void validateRequestBelongsToTeam(TradeRequest request, Long teamId) {
        if (!request.getTeam().getId().equals(teamId)) {
            throw new ForbiddenException("해당 팀의 매매요청이 아닙니다");
        }
    }
}
