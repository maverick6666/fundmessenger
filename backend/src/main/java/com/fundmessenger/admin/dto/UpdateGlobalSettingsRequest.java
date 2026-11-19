package com.fundmessenger.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateGlobalSettingsRequest {
    private String driveAllowedFormats;  // "pdf" 등 쉼표 구분

    @Min(1)
    @Max(500)
    private Integer driveMaxFileSizeMb;

    @Min(0)
    @Max(100)
    private Integer aiCreditCost;

    @Min(0)
    @Max(1000)
    private Integer defaultDailyAiLimit;

    @Min(1)
    @Max(50)
    private Integer dailyCommentBotMin;

    @Min(1)
    @Max(50)
    private Integer dailyCommentBotMax;
}
