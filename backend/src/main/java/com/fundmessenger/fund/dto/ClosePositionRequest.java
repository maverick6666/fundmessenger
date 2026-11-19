package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ClosePositionRequest {

    @NotNull(message = "매도가는 필수입니다")
    private BigDecimal sellPrice;

    @NotNull(message = "매도 수량은 필수입니다")
    private BigDecimal sellQuantity;
}
