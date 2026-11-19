package com.fundmessenger.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AiGenerateRequest {
    @NotNull
    private Long driveFileId;

    private Long boardId;  // nullable — 게시판 없이 팀에만 연결 가능

    /** 크레딧 차감 대상 팀 (M:N이므로 사용자 선택) */
    @NotNull
    private Long teamId;

    private List<Long> semiCategoryIds;
}
