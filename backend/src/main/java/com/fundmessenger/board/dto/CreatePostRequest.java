package com.fundmessenger.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class CreatePostRequest {
    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자 이내로 작성해주세요")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    /** 에디터의 구조화된 JSON 원본 (optional, legacy markdown-only 요청 허용) */
    private String contentJson;

    private Long aiJobId;

    /** 게시판 ID 목록 (최소 1개 필수, M:N) */
    @NotEmpty(message = "게시판을 최소 1개 선택해야 합니다")
    private List<Long> boardIds;

    /** 태그 ID 목록 (최소 1개 필수) */
    @NotEmpty(message = "태그를 최소 1개 선택해야 합니다")
    private List<Long> semiCategoryIds;

    /** 팀 ID 목록 (선택, 0개 = 무소속) */
    private List<Long> teamIds;
}
