package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CreatePositionRequest {

    @NotBlank(message = "종목 코드는 필수입니다")
    private String ticker;

    private String tickerName;

    @NotBlank(message = "시장은 필수입니다")
    private String market;

    @NotNull(message = "매수가는 필수입니다")
    @DecimalMin(value = "0.01", message = "매수가는 0보다 커야 합니다")
    private BigDecimal buyPrice;

    @NotNull(message = "수량은 필수입니다")
    @DecimalMin(value = "0.00000001", message = "수량은 0보다 커야 합니다")
    private BigDecimal quantity;

    private List<Map<String, Object>> buyPlan;
    private List<Map<String, Object>> takeProfitTargets;
    private List<Map<String, Object>> stopLossTargets;
}
