package com.fundmessenger.ai.dto;

import com.fundmessenger.ai.entity.AiGenerationJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiGenerationJobResponse {
    private Long jobId;
    private String status;
    private Long driveFileId;
    private String driveFileName;
    private Long linkedPostId;
    private String linkedPostTitle;
    private boolean publishable;
    private Long boardId;
    private String resultMarkdown;
    private String errorMessage;
    private String processingStep;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static AiGenerationJobResponse from(AiGenerationJob job) {
        return AiGenerationJobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .driveFileId(job.getDriveFile().getId())
                .driveFileName(job.getDriveFile().getOriginalName())
                .linkedPostId(job.getDriveFile().getLinkedPost() != null ? job.getDriveFile().getLinkedPost().getId() : null)
                .linkedPostTitle(job.getDriveFile().getLinkedPost() != null ? job.getDriveFile().getLinkedPost().getTitle() : null)
                .publishable(job.getStatus() == AiGenerationJob.JobStatus.COMPLETED && job.getDriveFile().getLinkedPost() == null)
                .boardId(job.getBoard() != null ? job.getBoard().getId() : null)
                .resultMarkdown(job.getStatus() == AiGenerationJob.JobStatus.COMPLETED ? job.getResultMarkdown() : null)
                .errorMessage(job.getStatus() == AiGenerationJob.JobStatus.FAILED ? job.getErrorMessage() : null)
                .processingStep(job.getProcessingStep())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
