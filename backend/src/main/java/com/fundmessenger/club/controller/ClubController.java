package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.*;
import com.fundmessenger.club.service.ClubMemberService;
import com.fundmessenger.club.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}")
@RequiredArgsConstructor
public class ClubController {

    private final ClubMemberService clubMemberService;
    private final TeamService teamService;

    /** 멤버 목록 */
    @GetMapping("/members")
    public ResponseEntity<List<ClubMemberResponse>> getMembers(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.getMembers(clubId, userId));
    }

    /** 팀 목록 */
    @GetMapping("/teams")
    public ResponseEntity<List<TeamResponse>> getTeams(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.getTeams(clubId, userId));
    }

    /** 팀 상세 + 팀원 */
    @GetMapping("/teams/{teamId}")
    public ResponseEntity<TeamDetailResponse> getTeam(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(teamService.getTeam(clubId, teamId, userId));
    }

    /** 매니저 임명 (팀장만) */
    @PutMapping("/teams/{teamId}/members/{membershipId}/manager")
    public ResponseEntity<Void> appointManager(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        teamService.appointManager(clubId, teamId, membershipId, userId);
        return ResponseEntity.ok().build();
    }

    /** 매니저 해제 (팀장만) */
    @DeleteMapping("/teams/{teamId}/members/{membershipId}/manager")
    public ResponseEntity<Void> revokeManager(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long membershipId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        teamService.revokeManager(clubId, teamId, membershipId, userId);
        return ResponseEntity.ok().build();
    }

}
