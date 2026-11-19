package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.WritePermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateBoardRequest {
    @NotBlank(message = "게시판 이름은 필수입니다")
    private String name;

    /** null이면 동아리 전체 게시판, 값이 있으면 팀 전용 게시판 */
    private Long teamId;

    /** 쓰기 권한 (기본: EVERYONE) */
    private WritePermission writePermission;

    /** 표시 순서 (기본: 0) */
    private Integer displayOrder;

    /** 게시판 설명 (선택) */
    @Size(max = 200, message = "설명은 200자 이내여야 합니다")
    private String description;
}
