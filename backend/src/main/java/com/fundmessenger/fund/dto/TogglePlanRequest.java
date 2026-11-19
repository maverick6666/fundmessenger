package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TogglePlanRequest {

    @NotBlank(message = "planType은 필수입니다 (BUY, TAKE_PROFIT, STOP_LOSS)")
    private String planType;

    private int index;
}
