package com.fundmessenger.admin.controller;

import com.fundmessenger.admin.dto.GlobalSettingsResponse;
import com.fundmessenger.admin.dto.MembershipResponse;
import com.fundmessenger.admin.dto.UpdateGlobalSettingsRequest;
import com.fundmessenger.admin.service.AdminService;
import com.fundmessenger.admin.service.ModelDataService;
import com.fundmessenger.jongtobang.service.NewsPipelineService;
import com.fundmessenger.jongtobang.service.StockMasterService;
import com.fundmessenger.jongtobang.service.SurgeReasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ModelDataService modelDataService;
    private final NewsPipelineService newsPipelineService;
    private final StockMasterService stockMasterService;
    private final SurgeReasonService surgeReasonService;

    // ========== 대시보드 ==========

    /** 운영 대시보드 통계 */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getDashboardStats(userId));
    }

    // ========== 학교 관리 ==========

    /** 전체 학교 목록 (동아리 수 포함) */
    @GetMapping("/schools")
    public ResponseEntity<?> getSchools(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getSchoolsWithStats(userId));
    }

    /** 학교 등록 */
    @PostMapping("/schools")
    public ResponseEntity<?> createSchool(
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        return ResponseEntity.ok(adminService.createSchool(name, userId));
    }

    // ========== 동아리 관리 ==========

    /** 특정 학교의 동아리 목록 (멤버 수 포함) */
    @GetMapping("/schools/{schoolId}/clubs")
    public ResponseEntity<?> getClubs(
            @PathVariable Long schoolId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getClubsWithStats(schoolId, userId));
    }

    /** 동아리 등록 */
    @PostMapping("/schools/{schoolId}/clubs")
    public ResponseEntity<?> createClub(
            @PathVariable Long schoolId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        return ResponseEntity.ok(adminService.createClub(schoolId, name, userId));
    }

    // ========== 크레딧 관리 ==========

    /** 크레딧 지급 */
    @PostMapping("/clubs/{clubId}/credits/grant")
    public ResponseEntity<?> grantCredits(
            @PathVariable Long clubId,
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        int amount = ((Number) body.get("amount")).intValue();
        String description = (String) body.get("description");
        return ResponseEntity.ok(adminService.grantCredits(clubId, amount, description, userId));
    }

    /** 크레딧 차감 */
    @PostMapping("/clubs/{clubId}/credits/revoke")
    public ResponseEntity<?> revokeCredits(
            @PathVariable Long clubId,
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        int amount = ((Number) body.get("amount")).intValue();
        String description = (String) body.get("description");
        return ResponseEntity.ok(adminService.revokeCredits(clubId, amount, description, userId));
    }

    /** 크레딧 지급/차감 내역 조회 */
    @GetMapping("/clubs/{clubId}/credits/history")
    public ResponseEntity<?> getCreditHistory(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getCreditHistory(clubId, userId));
    }

    // ========== 글로벌 설정 (드라이브 정책 등) ==========

    /** 글로벌 설정 조회 */
    @GetMapping("/settings")
    public ResponseEntity<GlobalSettingsResponse> getGlobalSettings(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getGlobalSettings(userId));
    }

    /** 글로벌 설정 수정 */
    @PutMapping("/settings")
    public ResponseEntity<GlobalSettingsResponse> updateGlobalSettings(
            @RequestBody UpdateGlobalSettingsRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.updateGlobalSettings(request, userId));
    }

    // ========== 구독 관리 ==========

    /** 구독 활성화 */
    @PostMapping("/clubs/{clubId}/subscription/activate")
    public ResponseEntity<?> activateSubscription(
            @PathVariable Long clubId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        LocalDate startDate = LocalDate.parse(body.get("startDate"));
        LocalDate endDate = LocalDate.parse(body.get("endDate"));
        return ResponseEntity.ok(adminService.activateSubscription(clubId, startDate, endDate, userId));
    }

    /** 구독 연장 */
    @PostMapping("/clubs/{clubId}/subscription/extend")
    public ResponseEntity<?> extendSubscription(
            @PathVariable Long clubId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        LocalDate newEndDate = LocalDate.parse(body.get("endDate"));
        return ResponseEntity.ok(adminService.extendSubscription(clubId, newEndDate, userId));
    }

    /** 구독 해지 */
    @PostMapping("/clubs/{clubId}/subscription/deactivate")
    public ResponseEntity<?> deactivateSubscription(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.deactivateSubscription(clubId, userId));
    }

    // ========== 동아리 멤버 관리 ==========

    /** 동아리의 승인된 멤버 목록 */
    @GetMapping("/clubs/{clubId}/members")
    public ResponseEntity<List<MembershipResponse>> getClubMembers(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getClubMembers(clubId, userId));
    }

    /** 동아리 멤버 태그 변경 (SYSTEM 태그) */
    @PutMapping("/clubs/{clubId}/members/{membershipId}/tag")
    public ResponseEntity<MembershipResponse> changeClubMemberTag(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        String tagName = body.get("tagName");
        String permissionLevel = body.get("permissionLevel");
        return ResponseEntity.ok(adminService.changeClubMemberTag(clubId, membershipId, tagName, permissionLevel, userId));
    }

    // ========== 가입 신청 관리 ==========

    /** 특정 동아리의 대기 중인 가입 신청 목록 */
    @GetMapping("/clubs/{clubId}/pending-memberships")
    public ResponseEntity<List<MembershipResponse>> getPendingByClub(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getPendingByClub(clubId, userId));
    }

    /** 전체 대기 중인 가입 신청 목록 */
    @GetMapping("/pending-memberships")
    public ResponseEntity<List<MembershipResponse>> getPendingMemberships(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.getPendingMemberships(userId));
    }

    /** 가입 신청 승인 */
    @PostMapping("/memberships/{id}/approve")
    public ResponseEntity<MembershipResponse> approveMembership(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.approveMembership(id, userId));
    }

    /** 가입 신청 거절 */
    @PostMapping("/memberships/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectMembership(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        adminService.rejectMembership(id, userId);
        return ResponseEntity.ok(Map.of("message", "거절되었습니다"));
    }

    // ========== 뉴스 파이프라인 (글로벌 — 테스트용) ==========

    /** 뉴스 파이프라인 수동 실행 (슈퍼어드민 전용, 글로벌) */
    @PostMapping("/news-pipeline/run")
    public ResponseEntity<Map<String, Object>> runNewsPipeline(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        adminService.requireSuperAdmin(userId);
        return ResponseEntity.ok(newsPipelineService.runPipelineManual());
    }

    /** 깨진 인코딩 후속 업데이트 헤더 일괄 수정 (1회성) */
    @PostMapping("/news-pipeline/fix-encoding")
    public ResponseEntity<Map<String, Object>> fixBrokenEncoding(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        adminService.requireSuperAdmin(userId);
        int fixed = newsPipelineService.fixBrokenFollowupHeaders();
        return ResponseEntity.ok(Map.of("fixed", fixed, "message", fixed + "건 인코딩 수정 완료"));
    }

    /** 급등 종목 이유 검색 수동 실행 (슈퍼어드민 전용, 글로벌) */
    @PostMapping("/news-pipeline/surge-reason")
    public ResponseEntity<Map<String, Object>> runSurgeReason(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        adminService.requireSuperAdmin(userId);
        long start = System.currentTimeMillis();
        int created = surgeReasonService.runSurgeReasonPipeline();
        return ResponseEntity.ok(Map.of(
                "created", created,
                "elapsedMs", System.currentTimeMillis() - start,
                "message", created > 0 ? created + "건 급등 이유 뉴스 생성" : "급등 종목 없거나 이미 뉴스 있음"
        ));
    }

    // ========== 종목 마스터 동기화 ==========

    /** 전종목 마스터 동기화 (슈퍼어드민 전용) */
    @PostMapping("/stocks/sync")
    public ResponseEntity<Map<String, Integer>> syncStockMaster(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        adminService.requireSuperAdmin(userId);
        return ResponseEntity.ok(stockMasterService.syncAllStocks());
    }

    // ========== 모델 데이터 관리 ==========

    /** 모델 데이터 현황 조회 */
    @GetMapping("/model-data/stats")
    public ResponseEntity<Map<String, Object>> getModelDataStats(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(modelDataService.getStats(userId));
    }

    /** 모델 데이터 내보내기 (ZIP 다운로드, DB 미삭제) */
    @PostMapping("/model-data/export")
    public ResponseEntity<byte[]> exportModelData(
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        LocalDate from = LocalDate.parse(body.get("from"));
        LocalDate to = LocalDate.parse(body.get("to"));

        ModelDataService.ExportResult result = modelDataService.export(from, to, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Export-Id", result.exportId())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(result.zipBytes());
    }

    /** 다운로드 확인 후 해당 기간 DB 삭제 */
    @DeleteMapping("/model-data/{exportId}")
    public ResponseEntity<Map<String, Object>> deleteModelData(
            @PathVariable String exportId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(modelDataService.deleteExportedData(exportId, userId));
    }
}
