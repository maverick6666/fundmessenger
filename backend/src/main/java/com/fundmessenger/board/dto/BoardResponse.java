package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import com.fundmessenger.board.entity.WritePermission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BoardResponse {
    private Long id;
    private String name;
    private BoardType boardType;
    private long postCount;
    private WritePermission writePermission;
    private boolean canWrite;
    private Integer displayOrder;
    private String description;
    private Long folderId;
    private LocalDateTime createdAt;

    public static BoardResponse from(Board board, long postCount, boolean canWrite) {
        return BoardResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .boardType(board.getBoardType())
                .postCount(postCount)
                .writePermission(board.getWritePermission())
                .canWrite(canWrite)
                .displayOrder(board.getDisplayOrder())
                .description(board.getDescription())
                .folderId(board.getAdminFolder() != null ? board.getAdminFolder().getId() : null)
                .createdAt(board.getCreatedAt())
                .build();
    }

    public static BoardResponse from(Board board, long postCount) {
        return from(board, postCount, true);
    }
}
