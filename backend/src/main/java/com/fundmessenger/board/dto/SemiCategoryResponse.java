package com.fundmessenger.board.dto;

import com.fundmessenger.board.entity.SemiCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SemiCategoryResponse {
    private Long id;
    private String name;
    private Integer displayOrder;
    private boolean generation;
    private boolean systemTag;
    private boolean enabled;
    private Long folderId;
    private LocalDateTime createdAt;

    public static SemiCategoryResponse from(SemiCategory category) {
        return SemiCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .generation(category.isGeneration())
                .systemTag(category.isSystemTag())
                .enabled(category.isEnabled())
                .folderId(category.getAdminFolder() != null ? category.getAdminFolder().getId() : null)
                .createdAt(category.getCreatedAt())
                .build();
    }
}
