package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.fund.dto.*;
import com.fundmessenger.fund.entity.AssetSnapshot;
import com.fundmessenger.fund.entity.CashAdjustment;
import com.fundmessenger.fund.service.AssetSnapshotService;
import com.fundmessenger.fund.service.BenchmarkService;
import com.fundmessenger.fund.service.CashAdjustmentService;
import com.fundmessenger.fund.service.FundStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final AssetSnapshotService snapshotService;
    private final CashAdjustmentService adjustmentService;
    private final BenchmarkService benchmarkService;
    private final FundStatsService fundStatsService;
    private final ClubAuthorizationService authService;
    private final MemberTagRepository memberTagRepository;

    /** 스냅샷 목록 (기간 필터) */
    @GetMapping("/snapshots")
    public ResponseEntity<List<AssetSnapshotResponse>> getSnapshots(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        List<AssetSnapshot> snapshots = snapshotService.getSnapshots(teamId, from, to);

        List<AssetSnapshotResponse> responses = snapshots.stream()
                .map(AssetSnapshotResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** 수동 스냅샷 생성 (매니저) */
    @PostMapping("/snapshots")
    public ResponseEntity<AssetSnapshotResponse> createSnapshot(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreateAssetSnapshotRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateTeamManager(userId, clubId, teamId);

        AssetSnapshot snapshot = snapshotService.createSnapshot(teamId, request);
        return ResponseEntity.ok(AssetSnapshotResponse.from(snapshot));
    }

    /** 입출금 이력 */
    @GetMapping("/cash-adjustments")
    public ResponseEntity<List<CashAdjustmentResponse>> getCashAdjustments(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        List<CashAdjustment> adjustments = adjustmentService.getAdjustments(teamId);

        List<CashAdjustmentResponse> responses = adjustments.stream()
                .map(CashAdjustmentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** 입출금 기록 (매니저) */
    @PostMapping("/cash-adjustments")
    public ResponseEntity<CashAdjustmentResponse> createCashAdjustment(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreateCashAdjustmentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        CashAdjustment adjustment = adjustmentService.recordAdjustment(teamId, request, membership);
        return ResponseEntity.ok(CashAdjustmentResponse.from(adjustment));
    }

    /** 벤치마크 비교 (포트폴리오 vs KOSPI vs S&P500) */
    @GetMapping("/benchmark")
    public ResponseEntity<BenchmarkComparisonResponse> getBenchmarkComparison(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "3M") String range,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        return ResponseEntity.ok(benchmarkService.compare(teamId, range));
    }

    /** 팀 멤버 랭킹 (실현손익 기준) */
    @GetMapping("/ranking")
    public ResponseEntity<List<MemberRankingResponse>> getTeamRanking(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        return ResponseEntity.ok(fundStatsService.getTeamRanking(teamId));
    }

    /** 내 개인 통계 */
    @GetMapping("/my")
    public ResponseEntity<MyStatsResponse> getMyStats(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        // 팀 멤버 검증
        if (!authService.isExecutive(membership)) {
            if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId)) {
                throw new SecurityException("해당 팀의 멤버가 아닙니다");
            }
        }

        return ResponseEntity.ok(fundStatsService.getMyStats(teamId, membership));
    }

    // ── Helpers ──

    private void validateTeamMember(Long userId, Long clubId, Long teamId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (authService.isExecutive(membership)) return;
        if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId)) {
            throw new SecurityException("해당 팀의 멤버가 아닙니다");
        }
    }
}
