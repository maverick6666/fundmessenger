package com.fundmessenger.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostCommentRequest {
    @NotBlank(message = "댓글 내용을 입력해주세요")
    @Size(max = 500, message = "댓글은 500자 이내로 작성해주세요")
    private String content;

    /** 대댓글 시 부모 댓글 ID (null이면 최상위 댓글) */
    private Long parentCommentId;
}
