package com.fundmessenger.fund.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionStatusInfo {
    private String status;   // needs_close, no_plan, normal, closed
    private String alert;    // danger, warning, null
    private String message;
}
