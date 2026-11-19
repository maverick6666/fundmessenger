package com.fundmessenger.board.entity;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.drive.entity.DriveFile;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_author_created", columnList = "author_id, created_at DESC"),
    @Index(name = "idx_post_featured", columnList = "featured_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** M:N 게시판 귀속 (0개 허용 — 게시판 삭제 시 연결만 끊김) */
    @ManyToMany
    @JoinTable(
        name = "post_boards",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "board_id")
    )
    @Builder.Default
    private Set<Board> boards = new HashSet<>();

    /** M:N 팀 귀속 (0개 = 무소속) — DriveFile.teams와 동일 패턴 */
    @ManyToMany
    @JoinTable(
        name = "post_teams",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private Set<Team> teams = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

    @Column(nullable = false, length = 200)
    private String title;

    /** TipTap 에디터 Markdown 콘텐츠 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** ProseMirror/Tiptap JSON 원본 문서 (nullable: legacy markdown 글) */
    @Column(columnDefinition = "TEXT")
    private String contentJson;

    /** M:N 태그 (최소 1개 필수 — Service 레벨 검증) */
    @ManyToMany
    @JoinTable(
        name = "post_semi_categories",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "semi_category_id")
    )
    @Builder.Default
    private Set<SemiCategory> semiCategories = new HashSet<>();

    /** 1:1 연동 드라이브 파일 (선택) — unique로 race condition 방지 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_drive_file_id", unique = true)
    private DriveFile linkedDriveFile;

    /** AI 생성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean aiGenerated = false;

    /** AI 생성 후 사용자가 수정했는지 */
    @Column(nullable = false)
    @Builder.Default
    private boolean aiEdited = false;

    @Column(nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** CB-7: 재조명된 날짜 (null = 미재조명) */
    private LocalDateTime featuredAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 첫 번째 게시판 반환 (하위호환 헬퍼) — boards 비어있으면 null */
    public Board getFirstBoard() {
        return boards.isEmpty() ? null : boards.iterator().next();
    }
}
