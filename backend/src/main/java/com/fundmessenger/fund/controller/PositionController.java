package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.fund.dto.*;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import com.fundmessenger.fund.entity.TeamSettings;
import com.fundmessenger.fund.repository.FundAuditLogRepository;
import com.fundmessenger.fund.repository.PlanSnapshotRepository;
import com.fundmessenger.fund.service.PositionService;
import com.fundmessenger.fund.service.TeamSettingsService;
import com.fundmessenger.jongtobang.dto.StockChartResponse;
import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.repository.StockRepository;
import com.fundmessenger.jongtobang.service.StockChartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;
    private final TeamSettingsService teamSettingsService;
    private final ClubAuthorizationService authService;
    private final MemberTagRepository memberTagRepository;
    private final StockRepository stockRepository;
    private final StockChartService stockChartService;
    private final FundAuditLogRepository auditLogRepository;
    private final PlanSnapshotRepository planSnapshotRepository;

    // ========== Position CRUD ==========

    /** 포지션 목록 (status 필터) */
    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        PositionStatus positionStatus = status != null ? PositionStatus.valueOf(status.toUpperCase()) : null;
        List<Position> positions = positionService.getPositions(teamId, positionStatus);

        List<PositionResponse> responses = positions.stream()
                .map(positionService::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** 포지션 상세 */
    @GetMapping("/positions/{positionId}")
    public ResponseEntity<PositionResponse> getPosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);
        return ResponseEntity.ok(positionService.toResponse(position));
    }

    /** 포지션 직접 생성 (매니저/팀장) */
    @PostMapping("/positions")
    public ResponseEntity<PositionResponse> createPosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreatePositionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.createPosition(teamId, request, membership);
        return ResponseEntity.ok(positionService.toResponse(position));
    }

    /** 포지션 종료 */
    @PostMapping("/positions/{positionId}/close")
    public ResponseEntity<PositionResponse> closePosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            @RequestBody @Valid ClosePositionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position closed = positionService.closePosition(positionId, request, membership);
        return ResponseEntity.ok(positionService.toResponse(closed));
    }

    /** 매니저 체결 확인 */
    @PostMapping("/positions/{positionId}/confirm")
    public ResponseEntity<PositionResponse> confirmPosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position confirmed = positionService.confirmPositionInfo(positionId);
        return ResponseEntity.ok(positionService.toResponse(confirmed));
    }

    /** 매매 계획 항목 완료 토글 */
    @PostMapping("/positions/{positionId}/toggle-plan")
    public ResponseEntity<PositionResponse> togglePlanItem(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            @RequestBody @Valid TogglePlanRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position toggled = positionService.togglePlanItem(positionId, request.getPlanType(), request.getIndex(), membership);
        return ResponseEntity.ok(positionService.toResponse(toggled));
    }

    /** 포지션 핵심 필드 인라인 수정 (averageBuyPrice, totalQuantity, tickerName) */
    @PatchMapping("/positions/{positionId}")
    public ResponseEntity<PositionResponse> updatePositionFields(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            @RequestBody UpdatePositionFieldsRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position updated = positionService.updatePositionFields(positionId, request, membership);
        return ResponseEntity.ok(positionService.toResponse(updated));
    }

    /** 매매 계획 업데이트 */
    @PatchMapping("/positions/{positionId}/plans")
    public ResponseEntity<PositionResponse> updatePlans(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            @RequestBody UpdatePositionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position updated = positionService.updatePlans(positionId, request, membership);
        return ResponseEntity.ok(positionService.toResponse(updated));
    }

    /** 포지션 삭제 (매니저) */
    @DeleteMapping("/positions/{positionId}")
    public ResponseEntity<Void> deletePosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        positionService.deletePosition(positionId);
        return ResponseEntity.noContent().build();
    }

    // ========== Position Publish ==========

    /** 종목 공개/비공개 토글 (매니저) */
    @PatchMapping("/positions/{positionId}/publish")
    public ResponseEntity<PositionResponse> togglePublish(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        Position saved = positionService.togglePublish(positionId);
        return ResponseEntity.ok(positionService.toResponse(saved));
    }

    // ========== Position Chart ==========

    /** 포지션 종목 차트 (ticker로 Stock 조회 후 KIS 차트 반환) */
    @GetMapping("/positions/{positionId}/chart")
    public ResponseEntity<StockChartResponse> getPositionChart(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            @RequestParam(defaultValue = "daily") String period,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        // ticker로 Stock 조회 → 차트 데이터 반환
        return stockRepository.findByStockCode(position.getTicker())
                .map(stock -> ResponseEntity.ok(stockChartService.getChart(stock.getId(), period)))
                .orElseGet(() -> ResponseEntity.ok(StockChartResponse.builder()
                        .stockCode(position.getTicker())
                        .period(period)
                        .prices(java.util.List.of())
                        .build()));
    }

    // ========== Team Settings ==========

    /** 팀 설정 조회 */
    @GetMapping("/settings")
    public ResponseEntity<TeamSettingsResponse> getSettings(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        TeamSettings settings = teamSettingsService.getOrCreateSettings(teamId);
        return ResponseEntity.ok(TeamSettingsResponse.from(settings));
    }

    /** 팀 설정 수정 */
    @PutMapping("/settings")
    public ResponseEntity<TeamSettingsResponse> updateSettings(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody UpdateTeamSettingsRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        TeamSettings settings = teamSettingsService.updateSettings(teamId, request);
        return ResponseEntity.ok(TeamSettingsResponse.from(settings));
    }

    /** 환전 기록 */
    @PostMapping("/settings/exchange")
    public ResponseEntity<TeamSettingsResponse> addExchange(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid ExchangeRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        TeamSettings settings = teamSettingsService.addExchange(teamId, request, userId);
        return ResponseEntity.ok(TeamSettingsResponse.from(settings));
    }

    // ========== Audit Logs & Plan Snapshots ==========

    /** 감사 로그 조회 */
    @GetMapping("/positions/{positionId}/audit-logs")
    public ResponseEntity<List<FundAuditLogResponse>> getAuditLogs(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        List<FundAuditLogResponse> logs = auditLogRepository
                .findByPositionIdOrderByCreatedAtDesc(positionId)
                .stream().map(FundAuditLogResponse::from).toList();
        return ResponseEntity.ok(logs);
    }

    /** 계획 스냅샷 조회 */
    @GetMapping("/positions/{positionId}/plan-snapshots")
    public ResponseEntity<List<PlanSnapshotResponse>> getPlanSnapshots(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        List<PlanSnapshotResponse> snapshots = planSnapshotRepository
                .findByPositionIdOrderByVersionDesc(positionId)
                .stream().map(PlanSnapshotResponse::from).toList();
        return ResponseEntity.ok(snapshots);
    }

    // ========== Team Member Requests ==========

    /** 토론 요청 (팀원용) */
    @PostMapping("/positions/{positionId}/request-discussion")
    public ResponseEntity<Void> requestDiscussion(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        positionService.requestDiscussion(positionId, membership);
        return ResponseEntity.ok().build();
    }

    /** 조기종료 요청 (팀원용) */
    @PostMapping("/positions/{positionId}/request-early-close")
    public ResponseEntity<Void> requestEarlyClose(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        validateTeamMember(userId, clubId, teamId);

        Position position = positionService.getPositionById(positionId);
        validatePositionBelongsToTeam(position, teamId);

        positionService.requestEarlyClose(positionId, membership);
        return ResponseEntity.ok().build();
    }

    // ========== Helpers ==========

    private void validateTeamMember(Long userId, Long clubId, Long teamId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        // 임원급은 모든 팀 조회 가능
        if (authService.isExecutive(membership)) return;
        // 팀원인지 확인
        if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId)) {
            throw new SecurityException("해당 팀의 멤버가 아닙니다");
        }
    }

    private void validatePositionBelongsToTeam(Position position, Long teamId) {
        if (!position.getTeam().getId().equals(teamId)) {
            throw new ForbiddenException("해당 팀의 포지션이 아닙니다");
        }
    }
}
