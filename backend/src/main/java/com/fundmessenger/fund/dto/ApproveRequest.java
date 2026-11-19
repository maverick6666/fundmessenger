package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ApproveRequest {

    /** null이면 요청값 사용 */
    @DecimalMin(value = "0.01", message = "체결 가격은 0보다 커야 합니다")
    private BigDecimal executedPrice;

    /** null이면 요청값 사용 */
    @DecimalMin(value = "0.00000001", message = "체결 수량은 0보다 커야 합니다")
    private BigDecimal executedQuantity;
}
