package com.fundmessenger.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyRequest {

    @NotNull(message = "동아리 ID는 필수입니다")
    private Long clubId;

    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @NotBlank(message = "학번은 필수입니다")
    @Pattern(regexp = "\\d{6,10}", message = "학번은 6~10자리 숫자여야 합니다")
    private String studentId;

    @NotBlank(message = "전화번호는 필수입니다")
    private String phone;
}
