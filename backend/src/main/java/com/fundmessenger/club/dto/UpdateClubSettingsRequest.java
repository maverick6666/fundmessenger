package com.fundmessenger.club.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateClubSettingsRequest {
    private String clubDescription;
    private String clubLogoUrl;  // /api/files/images/xxx 형태
    private String weeklyReviewBotName;  // 주간 리뷰 봇 발행 이름 (null이면 동아리명)
    private String aiAccessMode;  // EXECUTIVE_ONLY / ALL / NONE
}
