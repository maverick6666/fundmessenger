package com.fundmessenger.fund.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundmessenger.fund.entity.TeamSettings;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSettingsResponse {

    private Long id;
    private Long teamId;
    private BigDecimal initialCapitalKrw;
    private BigDecimal initialCapitalUsd;
    private List<Map<String, Object>> exchangeHistory;
    private Integer aiDailyLimit;
    private Integer aiUsageCount;
    @JsonProperty("profitPublic")
    private boolean profitPublic;
    private LocalDateTime updatedAt;

    public static TeamSettingsResponse from(TeamSettings s) {
        return TeamSettingsResponse.builder()
                .id(s.getId())
                .teamId(s.getTeam().getId())
                .initialCapitalKrw(s.getInitialCapitalKrw())
                .initialCapitalUsd(s.getInitialCapitalUsd())
                .exchangeHistory(s.getExchangeHistory())
                .aiDailyLimit(s.getAiDailyLimit())
                .aiUsageCount(s.getAiUsageCount())
                .profitPublic(s.isProfitPublic())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
