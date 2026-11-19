package com.fundmessenger.ai.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.board.entity.Board;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ai_generation_jobs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AiGenerationJob {

    public enum JobStatus { QUEUED, PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drive_file_id", nullable = false)
    private DriveFile driveFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;  // nullable — 게시판 없이 팀에만 연결 가능

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private ClubMembership requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Column(columnDefinition = "TEXT")
    private String resultMarkdown;

    @Column(length = 500)
    private String errorMessage;

    @Column(length = 100)
    private String processingStep;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean creditsCharged = false;

    private LocalDateTime creditsChargedAt;

    /** 모달에서 선택한 세미카테고리 ID 목록 (콤마 구분 문자열) */
    @Column(name = "semi_category_ids")
    private String semiCategoryIdsStr;

    public List<Long> getSemiCategoryIds() {
        if (semiCategoryIdsStr == null || semiCategoryIdsStr.isBlank()) return List.of();
        return java.util.Arrays.stream(semiCategoryIdsStr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).toList();
    }

    public void setSemiCategoryIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) { this.semiCategoryIdsStr = null; return; }
        this.semiCategoryIdsStr = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
