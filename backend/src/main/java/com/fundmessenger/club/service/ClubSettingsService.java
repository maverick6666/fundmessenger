package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.ClubSettingsResponse;
import com.fundmessenger.club.dto.UpdateClubSettingsRequest;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubSettings;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubSettingsService {

    private final ClubSettingsRepository settingsRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;

    /**
     * 설정 조회 (모든 멤버)
     * 설정이 없으면 기본값으로 자동 생성
     */
    public ClubSettingsResponse getSettings(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        ClubSettings settings = getOrCreate(clubId);
        return ClubSettingsResponse.from(settings);
    }

    /**
     * 설정 수정 (회장/VP)
     */
    @Transactional
    public ClubSettingsResponse updateSettings(Long clubId, UpdateClubSettingsRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        ClubSettings settings = getOrCreate(clubId);

        if (request.getClubDescription() != null) {
            settings.setClubDescription(request.getClubDescription().trim());
        } else {
            settings.setClubDescription(null);
        }
        if (request.getClubLogoUrl() != null) {
            settings.setClubLogoUrl(request.getClubLogoUrl().trim());
        } else {
            settings.setClubLogoUrl(null);
        }
        // weeklyReviewBotName: 빈 문자열이면 null 처리 (동아리명 사용)
        if (request.getWeeklyReviewBotName() != null && !request.getWeeklyReviewBotName().isBlank()) {
            settings.setWeeklyReviewBotName(request.getWeeklyReviewBotName().trim());
        } else {
            settings.setWeeklyReviewBotName(null);
        }

        // aiAccessMode
        if (request.getAiAccessMode() != null) {
            String mode = request.getAiAccessMode().trim().toUpperCase();
            if (mode.equals("EXECUTIVE_ONLY") || mode.equals("ALL") || mode.equals("NONE")) {
                settings.setAiAccessMode(mode);
            }
        }

        return ClubSettingsResponse.from(settings);
    }

    private ClubSettings getOrCreate(Long clubId) {
        return settingsRepository.findById(clubId)
                .orElseGet(() -> {
                    Club club = clubRepository.findById(clubId)
                            .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));
                    ClubSettings newSettings = ClubSettings.builder()
                            .club(club)
                            .build();
                    return settingsRepository.save(newSettings);
                });
    }
}
