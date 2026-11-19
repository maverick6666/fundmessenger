package com.fundmessenger.jongtobang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNewsCommentRequest {
    @NotBlank
    @Size(max = 1000)
    private String content;

    /** 대댓글 시 부모 댓글 ID */
    private Long parentCommentId;
}
