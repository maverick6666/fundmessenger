package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.ClubSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ClubSettingsResponse {
    private String clubDescription;
    private String clubLogoUrl;
    private boolean aiCommentBotEnabled;
    private String aiCommentBotScope;
    private String weeklyReviewBotName;
    private String aiAccessMode;

    public static ClubSettingsResponse from(ClubSettings s) {
        return ClubSettingsResponse.builder()
                .clubDescription(s.getClubDescription())
                .clubLogoUrl(s.getClubLogoUrl())
                .aiCommentBotEnabled(s.isAiCommentBotEnabled())
                .aiCommentBotScope(s.getAiCommentBotScope())
                .weeklyReviewBotName(s.getWeeklyReviewBotName())
                .aiAccessMode(s.getAiAccessMode())
                .build();
    }
}
