package com.fundmessenger.fund.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePositionFieldsRequest {
    private BigDecimal averageBuyPrice;
    private BigDecimal totalQuantity;
    private String tickerName;
}
