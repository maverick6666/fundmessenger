package com.fundmessenger.ai.controller;

import com.fundmessenger.ai.service.AiCommentBotService;
import com.fundmessenger.ai.service.AiCommentBotService.BotPersona;
import com.fundmessenger.club.entity.ClubSettings;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.fundmessenger.ai.service.AiCommentBotService.ContentType;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/ai-comment-bot")
@RequiredArgsConstructor
public class AiCommentBotController {

    private final AiCommentBotService commentBotService;
    private final ClubSettingsRepository settingsRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final UserRepository userRepository;

    // ── 설정 조회 ──

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(@PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubAdmin(userId, clubId);
        ClubSettings settings = getOrCreateSettings(clubId);
        return ResponseEntity.ok(Map.of(
                "enabled", settings.isAiCommentBotEnabled(),
                "scope", settings.getAiCommentBotScope()
        ));
    }

    // ── 설정 변경 ──

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@PathVariable Long clubId,
                                             @RequestBody BotSettingsRequest request,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubAdmin(userId, clubId);
        ClubSettings settings = getOrCreateSettings(clubId);

        if (request.getEnabled() != null) {
            settings.setAiCommentBotEnabled(request.getEnabled());

            // 최초 활성화 시 봇 멤버십 자동 생성
            if (request.getEnabled()) {
                for (BotPersona persona : BotPersona.values()) {
                    commentBotService.getOrCreateBotMembership(clubId, persona);
                }
            }
        }
        if (request.getScope() != null) {
            if (!request.getScope().matches("JONGTOBANG_ONLY|BOARD_ONLY|BOTH")) {
                return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 scope: " + request.getScope()));
            }
            settings.setAiCommentBotScope(request.getScope());
        }

        settingsRepository.save(settings);

        return ResponseEntity.ok(Map.of(
                "enabled", settings.isAiCommentBotEnabled(),
                "scope", settings.getAiCommentBotScope()
        ));
    }

    // ── 수동 트리거 (SUPER_ADMIN) ──

    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(@PathVariable Long clubId,
                                      @RequestBody(required = false) TriggerRequest request,
                                      Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // SUPER_ADMIN 확인
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isSuperAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "SUPER_ADMIN 전용"));
        }

        BotPersona persona = null;
        if (request != null && request.getPersona() != null) {
            try {
                persona = BotPersona.valueOf(request.getPersona());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 persona: " + request.getPersona()));
            }
        }

        ContentType forcedType = null;
        if (request != null && request.getContentType() != null) {
            try {
                forcedType = ContentType.valueOf(request.getContentType());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 contentType: " + request.getContentType()));
            }
        }

        Map<String, Object> result = commentBotService.executeRound(clubId, persona, forcedType);
        return ResponseEntity.ok(result);
    }

    // ── 헬퍼 ──

    private ClubSettings getOrCreateSettings(Long clubId) {
        return settingsRepository.findById(clubId)
                .orElseGet(() -> {
                    var club = clubRepository.findById(clubId)
                            .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));
                    ClubSettings newSettings = ClubSettings.builder().club(club).build();
                    return settingsRepository.save(newSettings);
                });
    }

    // ── DTO ──

    @Getter @Setter
    public static class BotSettingsRequest {
        private Boolean enabled;
        private String scope;
    }

    @Getter @Setter
    public static class TriggerRequest {
        private String persona;
        private String contentType;
    }
}
