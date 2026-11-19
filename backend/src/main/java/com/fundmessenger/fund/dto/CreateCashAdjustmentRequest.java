package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateCashAdjustmentRequest {

    @NotNull
    @Pattern(regexp = "DEPOSIT|WITHDRAWAL", message = "DEPOSIT 또는 WITHDRAWAL만 허용")
    private String adjustmentType;

    @NotBlank
    @Pattern(regexp = "KRW|USD|USDT", message = "KRW, USD, USDT만 허용")
    private String currency;

    @NotNull
    @DecimalMin(value = "0.01", message = "금액은 0보다 커야 합니다")
    private BigDecimal amount;

    private String memo;
}
