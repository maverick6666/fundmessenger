package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class RejectRequest {

    @NotBlank(message = "거절 사유는 필수입니다")
    private String rejectionReason;
}
