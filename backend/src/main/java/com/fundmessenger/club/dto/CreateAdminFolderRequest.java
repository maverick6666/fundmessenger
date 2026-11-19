package com.fundmessenger.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAdminFolderRequest {
    @NotBlank
    private String name;

    @NotNull
    private String folderType; // TAG, BOARD, CATEGORY, GENERATION

    /** 폴더 생성 시 함께 넣을 아이템 ID 목록 (드래그 합체 시) */
    private java.util.List<Long> itemIds;
}
