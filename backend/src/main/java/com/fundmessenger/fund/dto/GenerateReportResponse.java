package com.fundmessenger.fund.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GenerateReportResponse {

    private Long noteId;
    private String title;
    private String content;
    private int creditsUsed;
}
