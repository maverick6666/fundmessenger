package com.fundmessenger.fund.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UpdateTeamSettingsRequest {
    private BigDecimal initialCapitalKrw;
    private BigDecimal initialCapitalUsd;
    private Boolean profitPublic;
}
