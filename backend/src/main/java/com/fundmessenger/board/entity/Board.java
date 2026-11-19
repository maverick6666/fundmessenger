package com.fundmessenger.board.entity;

import com.fundmessenger.club.entity.AdminFolder;
import com.fundmessenger.club.entity.Club;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "boards",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "name"}),
       indexes = {
           @Index(name = "idx_board_club_order", columnList = "club_id, display_order")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** 게시판 유형: CUSTOM(수동), AI(AI 글) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BoardType boardType = BoardType.CUSTOM;

    /** 쓰기 권한: EVERYONE(기본), EXECUTIVE_ONLY, TEAM_ONLY */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WritePermission writePermission = WritePermission.EVERYONE;

    /** 표시 순서 (0부터, 낮을수록 위) */
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /** 게시판 설명 (선택) */
    @Column(length = 200)
    private String description;

    /** 어드민 폴더 (null이면 최상위) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_folder_id")
    private AdminFolder adminFolder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
