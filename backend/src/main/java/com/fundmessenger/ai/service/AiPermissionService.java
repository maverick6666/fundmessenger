package com.fundmessenger.ai.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.ClubSettings;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 글작성 권한 체크
 * ClubSettings.aiAccessMode 기반: EXECUTIVE_ONLY / ALL / NONE
 */
@Service
@RequiredArgsConstructor
public class AiPermissionService {

    private final ClubAuthorizationService authService;
    private final ClubSettingsRepository settingsRepository;

    public boolean hasAiPermission(Long userId, Long clubId) {
        try {
            ClubMembership membership = authService.validateActiveMember(userId, clubId);
            String mode = getAiAccessMode(clubId);

            return switch (mode) {
                case "ALL" -> true;
                case "NONE" -> false;
                default -> membership.isExecutive();
            };
        } catch (Exception e) {
            return false;
        }
    }

    public ClubMembership validateAiPermission(Long userId, Long clubId) {
        ClubMembership membership = authService.validateActiveMember(userId, clubId);
        String mode = getAiAccessMode(clubId);

        boolean hasPermission = switch (mode) {
            case "ALL" -> true;
            case "NONE" -> false;
            default -> membership.isExecutive();
        };

        if (!hasPermission) {
            throw new SecurityException("AI 글작성 권한이 없습니다");
        }
        return membership;
    }

    private String getAiAccessMode(Long clubId) {
        return settingsRepository.findById(clubId)
                .map(ClubSettings::getAiAccessMode)
                .orElse("ALL");
    }
}
