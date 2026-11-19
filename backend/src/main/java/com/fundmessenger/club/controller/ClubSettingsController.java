package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.ClubSettingsResponse;
import com.fundmessenger.club.dto.UpdateClubSettingsRequest;
import com.fundmessenger.club.service.ClubSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clubs/{clubId}/settings")
@RequiredArgsConstructor
public class ClubSettingsController {

    private final ClubSettingsService settingsService;

    /** 설정 조회 */
    @GetMapping
    public ResponseEntity<ClubSettingsResponse> getSettings(
            @PathVariable Long clubId,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(settingsService.getSettings(clubId, userId));
    }

    /** 설정 수정 (회장/VP) */
    @PutMapping
    public ResponseEntity<ClubSettingsResponse> updateSettings(
            @PathVariable Long clubId,
            @Valid @RequestBody UpdateClubSettingsRequest request,
            Authentication auth
    ) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(settingsService.updateSettings(clubId, request, userId));
    }
}
