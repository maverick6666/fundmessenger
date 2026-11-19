package com.fundmessenger.ai.dto;

import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.util.AiMarkdownTitleExtractor;
import com.fundmessenger.board.entity.SemiCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiEditorContentResponse {
    private Long jobId;
    private String title;
    private String markdown;
    private Long boardId;
    private Long driveFileId;
    private String driveFileName;
    private List<Long> semiCategoryIds;

    public static AiEditorContentResponse from(AiGenerationJob job) {
        List<Long> tagIds = job.getDriveFile().getSemiCategories().stream()
                .filter(sc -> !sc.isGeneration())
                .map(SemiCategory::getId)
                .toList();

        return AiEditorContentResponse.builder()
                .jobId(job.getId())
                .title(AiMarkdownTitleExtractor.extractTitle(job.getResultMarkdown()))
                .markdown(job.getResultMarkdown())
                .boardId(job.getBoard() != null ? job.getBoard().getId() : null)
                .driveFileId(job.getDriveFile().getId())
                .driveFileName(job.getDriveFile().getOriginalName())
                .semiCategoryIds(tagIds)
                .build();
    }
}
