package com.fundmessenger.admin.dto;

import com.fundmessenger.admin.entity.GlobalSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class GlobalSettingsResponse {
    private String driveAllowedFormats;
    private Integer driveMaxFileSizeMb;
    private Integer aiCreditCost;
    private Integer defaultDailyAiLimit;
    private Integer dailyCommentBotMin;
    private Integer dailyCommentBotMax;

    public static GlobalSettingsResponse from(GlobalSettings s) {
        return GlobalSettingsResponse.builder()
                .driveAllowedFormats(s.getDriveAllowedFormats())
                .driveMaxFileSizeMb(s.getDriveMaxFileSizeMb())
                .aiCreditCost(s.getAiCreditCost())
                .defaultDailyAiLimit(s.getDefaultDailyAiLimit())
                .dailyCommentBotMin(s.getDailyCommentBotMin())
                .dailyCommentBotMax(s.getDailyCommentBotMax())
                .build();
    }
}
