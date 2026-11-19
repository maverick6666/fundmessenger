package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CreateBuyRequest {

    @NotBlank(message = "종목 코드는 필수입니다")
    private String targetTicker;

    private String targetMarket;

    private String targetTickerName;

    @NotNull(message = "주문 수량은 필수입니다")
    @DecimalMin(value = "0.00000001", message = "수량은 0보다 커야 합니다")
    private BigDecimal orderQuantity;

    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다")
    private BigDecimal buyPrice;

    private List<Map<String, Object>> buyOrders;

    @DecimalMin(value = "0.01", message = "비중은 0보다 커야 합니다")
    @DecimalMax(value = "100.00", message = "비중은 100% 이하여야 합니다")
    private BigDecimal targetRatio;

    private List<Map<String, Object>> takeProfitTargets;

    private List<Map<String, Object>> stopLossTargets;

    @NotBlank(message = "매매 사유(memo)는 필수입니다")
    private String memo;
}
