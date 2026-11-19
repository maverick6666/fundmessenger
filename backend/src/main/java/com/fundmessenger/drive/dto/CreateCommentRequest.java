package com.fundmessenger.drive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommentRequest {
    @NotBlank
    @Size(max = 2000)
    private String content;

    /** null이면 전체 코멘트, 숫자면 해당 페이지 코멘트 */
    private Integer pageNumber;
}
