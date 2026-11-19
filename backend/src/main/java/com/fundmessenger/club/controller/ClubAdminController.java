package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.*;
import com.fundmessenger.club.entity.AdminFolderType;
import com.fundmessenger.club.service.*;
import com.fundmessenger.home.dto.ExecutiveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/admin")
@RequiredArgsConstructor
public class ClubAdminController {

    private final ClubMemberService clubMemberService;
    private final TeamService teamService;
    private final AdminFolderService adminFolderService;
    private final ClubDashboardService dashboardService;
    private final CarouselService carouselService;

    // ========== 대시보드 (임원급) ==========

    @GetMapping("/dashboard")
    public ResponseEntity<ClubDashboardResponse> getDashboard(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "MONTH") String period,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(dashboardService.getDashboard(clubId, userId, period));
    }

    // ========== 가입 요청 관리 (임원급) ==========

    @GetMapping("/join-requests")
    public ResponseEntity<List<JoinRequestResponse>> getJoinRequests(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.getJoinRequests(clubId, userId));
    }

    @PutMapping("/join-requests/{membershipId}/approve")
    public ResponseEntity<JoinRequestResponse> approveJoinRequest(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.approveJoinRequest(clubId, membershipId, userId));
    }

    @PutMapping("/join-requests/{membershipId}/reject")
    public ResponseEntity<JoinRequestResponse> rejectJoinRequest(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.rejectJoinRequest(clubId, membershipId, userId));
    }

    // ========== 멤버 태그 관리 (임원급) ==========

    /** 멤버에게 태그 부여 */
    @PostMapping("/members/{membershipId}/tags")
    public ResponseEntity<ClubMemberResponse> assignTag(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            @RequestBody Map<String, Long> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        Long tagDefinitionId = body.get("tagDefinitionId");
        return ResponseEntity.ok(
                clubMemberService.assignTag(clubId, membershipId, tagDefinitionId, userId));
    }

    // ========== 태그 정의 관리 (임원급) ==========

    @GetMapping("/tags")
    public ResponseEntity<List<TagDefinitionResponse>> getTagDefinitions(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.getTagDefinitions(clubId, userId));
    }

    @PostMapping("/tags")
    public ResponseEntity<TagDefinitionResponse> createCustomTag(
            @PathVariable Long clubId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                clubMemberService.createCustomTag(clubId, body.get("name"), body.get("permissionLevel"), userId));
    }

    @PutMapping("/tags/{tagId}")
    public ResponseEntity<TagDefinitionResponse> updateTagDefinition(
            @PathVariable Long clubId,
            @PathVariable Long tagId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                clubMemberService.updateTagDefinition(clubId, tagId, body.get("name"), body.get("permissionLevel"), userId));
    }

    @DeleteMapping("/tags/{tagId}")
    public ResponseEntity<Map<String, String>> deleteTagDefinition(
            @PathVariable Long clubId,
            @PathVariable Long tagId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        clubMemberService.deleteTagDefinition(clubId, tagId, userId);
        return ResponseEntity.ok(Map.of("message", "태그가 삭제되었습니다"));
    }

    // ========== 팀 CRUD (임원급) ==========

    @PostMapping("/teams")
    public ResponseEntity<TeamResponse> createTeam(
            @PathVariable Long clubId,
            @Valid @RequestBody CreateTeamRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.createTeam(clubId, request, userId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.updateTeam(clubId, teamId, request, userId));
    }

    /** 팀명 변경 */
    @PutMapping("/teams/{teamId}/name")
    public ResponseEntity<Map<String, String>> updateTeamName(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        String newName = body.get("name");
        teamService.updateTeamName(clubId, teamId, newName, userId);
        return ResponseEntity.ok(Map.of("message", "팀명이 변경되었습니다"));
    }

    /** 팀 해체 (soft delete) */
    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<Map<String, String>> dissolveTeam(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        teamService.dissolveTeam(clubId, teamId, userId);
        return ResponseEntity.ok(Map.of("message", "팀이 해체되었습니다"));
    }

    // ========== 팀원 관리 (임원급/팀장) ==========

    @PostMapping("/teams/{teamId}/members")
    public ResponseEntity<TeamDetailResponse> addMembers(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @Valid @RequestBody AddTeamMembersRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                teamService.addMembers(clubId, teamId, request.getClubMembershipIds(), userId));
    }

    /** 팀원 제거 (tagId 기반) */
    @DeleteMapping("/teams/{teamId}/members/{tagId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long tagId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        teamService.removeMember(clubId, teamId, tagId, userId);
        return ResponseEntity.ok(Map.of("message", "팀원이 제거되었습니다"));
    }

    /** 팀장 해제 (팀장 → 팀원, 팀은 유지) */
    @DeleteMapping("/teams/{teamId}/leader")
    public ResponseEntity<TeamDetailResponse> removeTeamLeader(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.removeTeamLeader(clubId, teamId, userId));
    }

    /** 팀장 변경 */
    @PutMapping("/teams/{teamId}/leader")
    public ResponseEntity<TeamDetailResponse> changeTeamLeader(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody Map<String, Long> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        Long clubMembershipId = body.get("clubMembershipId");
        return ResponseEntity.ok(
                teamService.changeTeamLeader(clubId, teamId, clubMembershipId, userId));
    }

    /** 펀드팀 지정/해제 */
    @PutMapping("/teams/{teamId}/fund")
    public ResponseEntity<Map<String, String>> setFundTeam(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody Map<String, Boolean> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean fund = body.getOrDefault("fund", false);
        teamService.setFundTeam(clubId, teamId, fund, userId);
        return ResponseEntity.ok(Map.of("message", fund ? "펀드팀으로 지정되었습니다" : "일반 팀으로 변경되었습니다"));
    }

    /** 미배정 멤버 조회 */
    @GetMapping("/unassigned-members")
    public ResponseEntity<List<ClubMemberResponse>> getUnassignedMembers(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.getUnassignedMembers(clubId, userId));
    }

    // ========== 멤버 비활성화/삭제 (임원급) ==========

    /** 멤버 비활성화 (APPROVED → INACTIVE) */
    @PutMapping("/members/{membershipId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateMember(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        clubMemberService.deactivateMember(clubId, membershipId, userId);
        return ResponseEntity.ok(Map.of("message", "멤버가 비활성화되었습니다"));
    }

    /** 멤버 삭제 (hard delete) */
    @DeleteMapping("/members/{membershipId}")
    public ResponseEntity<Map<String, String>> deleteMember(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        clubMemberService.deleteMember(clubId, membershipId, userId);
        return ResponseEntity.ok(Map.of("message", "멤버가 삭제되었습니다"));
    }

    // ========== 회장단 캐러셀 (임원급) ==========

    @GetMapping("/carousel")
    public ResponseEntity<List<ExecutiveResponse>> getCarouselMembers(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(carouselService.getCarouselMembers(clubId, userId));
    }

    @PostMapping("/carousel")
    public ResponseEntity<ExecutiveResponse> addCarouselMember(
            @PathVariable Long clubId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long membershipId = ((Number) body.get("membershipId")).longValue();
        String roleName = (String) body.getOrDefault("roleName", "멤버");
        return ResponseEntity.ok(carouselService.addCarouselMember(clubId, membershipId, roleName, userId));
    }

    @PutMapping("/carousel/{carouselId}")
    public ResponseEntity<ExecutiveResponse> updateCarouselMember(
            @PathVariable Long clubId,
            @PathVariable Long carouselId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String roleName = (String) body.get("roleName");
        String bio = (String) body.get("bio");
        Integer displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null;
        return ResponseEntity.ok(carouselService.updateCarouselMember(clubId, carouselId, roleName, bio, displayOrder, userId));
    }

    @DeleteMapping("/carousel/{carouselId}")
    public ResponseEntity<Map<String, String>> removeCarouselMember(
            @PathVariable Long clubId,
            @PathVariable Long carouselId,
            @RequestParam(defaultValue = "false") boolean removeExecutive,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        carouselService.removeCarouselMember(clubId, carouselId, removeExecutive, userId);
        return ResponseEntity.ok(Map.of("message", "회장단에서 제거되었습니다"));
    }

    // ========== EXECUTIVE 권한 토글 (임원급) ==========

    @PutMapping("/members/{membershipId}/executive")
    public ResponseEntity<Map<String, String>> toggleExecutive(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            @RequestBody Map<String, Boolean> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean executive = body.getOrDefault("executive", false);
        carouselService.toggleExecutive(clubId, membershipId, executive, userId);
        return ResponseEntity.ok(Map.of("message", executive ? "관리자 권한이 부여되었습니다" : "관리자 권한이 해제되었습니다"));
    }

    // ========== 어드민 폴더 (리스트 폴더링) ==========

    @GetMapping("/folders")
    public ResponseEntity<List<AdminFolderResponse>> getFolders(
            @PathVariable Long clubId,
            @RequestParam String type,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                adminFolderService.getFolders(clubId, AdminFolderType.valueOf(type), userId));
    }

    @PostMapping("/folders")
    public ResponseEntity<AdminFolderResponse> createFolder(
            @PathVariable Long clubId,
            @Valid @RequestBody CreateAdminFolderRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminFolderService.createFolder(clubId, request, userId));
    }

    @PutMapping("/folders/{folderId}/name")
    public ResponseEntity<AdminFolderResponse> renameFolder(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                adminFolderService.renameFolder(clubId, folderId, body.get("name"), userId));
    }

    @PutMapping("/folders/{folderId}/toggle")
    public ResponseEntity<AdminFolderResponse> toggleFolderCollapsed(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(
                adminFolderService.toggleCollapsed(clubId, folderId, userId));
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Map<String, String>> deleteFolder(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        adminFolderService.deleteFolder(clubId, folderId, userId);
        return ResponseEntity.ok(Map.of("message", "폴더가 삭제되었습니다"));
    }

    @PutMapping("/folders/{folderId}/items")
    public ResponseEntity<Map<String, String>> assignItemsToFolder(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            @RequestBody Map<String, List<Long>> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        adminFolderService.assignItems(clubId, folderId, body.get("itemIds"), userId);
        return ResponseEntity.ok(Map.of("message", "아이템이 폴더에 배정되었습니다"));
    }

    @PutMapping("/folders/unassign")
    public ResponseEntity<Map<String, String>> unassignItems(
            @PathVariable Long clubId,
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        @SuppressWarnings("unchecked")
        List<Long> itemIds = ((List<Number>) body.get("itemIds")).stream().map(Number::longValue).toList();
        String folderType = (String) body.get("folderType");
        adminFolderService.unassignItems(clubId, itemIds, folderType, userId);
        return ResponseEntity.ok(Map.of("message", "아이템이 폴더에서 해제되었습니다"));
    }
}
