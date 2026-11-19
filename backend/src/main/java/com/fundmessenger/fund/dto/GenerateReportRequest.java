package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GenerateReportRequest {

    @NotEmpty(message = "선택 항목이 비어있습니다")
    private List<String> selectedItems;

    /** 조회 기간 (일 단위, 기본값 7) */
    private Integer periodDays = 7;
}
