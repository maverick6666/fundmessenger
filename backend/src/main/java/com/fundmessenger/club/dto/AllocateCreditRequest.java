package com.fundmessenger.club.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AllocateCreditRequest {

    @NotNull
    private Long teamId;

    @NotNull
    @Min(1)
    private Integer amount;

    private String description;
}
