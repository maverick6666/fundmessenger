package com.fundmessenger.jongtobang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateThreadRequest {
    /** 제목 — 미입력 시 content 첫 줄에서 자동 생성 */
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String content;
}
