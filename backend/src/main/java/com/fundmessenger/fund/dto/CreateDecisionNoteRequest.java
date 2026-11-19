package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDecisionNoteRequest {

    private Long positionId;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private String noteType; // "DECISION" or "REPORT"
}
