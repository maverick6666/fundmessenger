package com.fundmessenger.drive.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDriveFileRequest {
    private List<Long> teamIds;
    private List<Long> folderIds;

    @NotEmpty(message = "태그를 하나 이상 선택해야 합니다")
    private List<Long> semiCategoryIds;
}
