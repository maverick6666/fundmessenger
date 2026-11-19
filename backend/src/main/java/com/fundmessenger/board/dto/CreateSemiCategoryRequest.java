package com.fundmessenger.board.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateSemiCategoryRequest {
    @NotBlank(message = "카테고리 이름은 필수입니다")
    private String name;

    /** 표시 순서 (기본: 0) */
    private Integer displayOrder;
}
