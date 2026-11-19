package com.fundmessenger.fund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiDecisionRequest {

    @NotNull(message = "chatRoomId는 필수입니다")
    private Long chatRoomId;
}
