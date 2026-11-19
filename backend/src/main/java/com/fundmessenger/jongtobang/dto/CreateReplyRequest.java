package com.fundmessenger.jongtobang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReplyRequest {
    @NotBlank
    @Size(max = 2000)
    private String content;

    /** 대댓글 시 부모 답글 ID */
    private Long parentReplyId;
}
