package com.fundmessenger.drive.entity;

import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.drive.entity.DriveFolder;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "drive_files", indexes = {
    @Index(name = "idx_drivefile_club_created", columnList = "club_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DriveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** M:N 팀 귀속 (0개 = 무소속) */
    @ManyToMany
    @JoinTable(
        name = "drive_file_teams",
        joinColumns = @JoinColumn(name = "drive_file_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private Set<Team> teams = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private ClubMembership uploader;

    /** 원본 파일명 */
    @Column(nullable = false)
    private String originalName;

    /** 저장된 파일명 (UUID) */
    @Column(nullable = false, unique = true)
    private String storedName;

    /** 파일 크기 (bytes) */
    @Column(nullable = false)
    private Long fileSize;

    /** MIME 타입 (예: application/pdf) */
    private String mimeType;

    /** 설명 (선택) */
    private String description;

    /** 1:1 연동 게시글 (선택) — unique로 race condition 방지 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_post_id", unique = true)
    private Post linkedPost;

    /** M:N 태그 (최소 1개 필수 — Service 레벨 검증) */
    @ManyToMany
    @JoinTable(
        name = "drive_file_semi_categories",
        joinColumns = @JoinColumn(name = "drive_file_id"),
        inverseJoinColumns = @JoinColumn(name = "semi_category_id")
    )
    @Builder.Default
    private Set<SemiCategory> semiCategories = new HashSet<>();

    /** M:N 폴더 귀속 (0개 = 폴더 미지정) */
    @ManyToMany
    @JoinTable(
        name = "drive_file_folders",
        joinColumns = @JoinColumn(name = "drive_file_id"),
        inverseJoinColumns = @JoinColumn(name = "drive_folder_id")
    )
    @Builder.Default
    private Set<DriveFolder> folders = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
