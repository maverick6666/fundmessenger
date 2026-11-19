package com.fundmessenger.fund.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PriceQuoteResponse {

    private String ticker;
    private String market;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal changeRate;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
}
