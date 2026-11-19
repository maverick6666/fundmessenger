package com.fundmessenger.chat.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class VoteRequest {
    @NotNull @Size(min = 1)
    private List<Integer> selectedOptions;
}
