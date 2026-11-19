package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CreateSellRequest {

    @NotNull(message = "포지션 ID는 필수입니다")
    private Long positionId;

    @NotNull(message = "매도 수량은 필수입니다")
    private BigDecimal sellQuantity;

    private BigDecimal sellPrice;

    private String sellReason;

    @NotBlank(message = "매매 사유(memo)는 필수입니다")
    private String memo;
}
