package com.fundmessenger.board.entity;

import com.fundmessenger.club.entity.AdminFolder;
import com.fundmessenger.club.entity.Club;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "semi_categories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "name"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SemiCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** 카테고리명: "1주차 세미", "기업분석" 등 */
    @Column(nullable = false)
    private String name;

    /** 표시 순서 (0부터, 낮을수록 위) */
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /** 기수 태그 여부 (true = "33기" 등 기수 태그, false = 일반 태그) */
    @Column(nullable = false)
    @Builder.Default
    private boolean generation = false;

    /** 시스템 자동생성 태그 여부 (true = 시스템이 만든 태그, 삭제 불가) */
    @Column(nullable = false)
    @Builder.Default
    private boolean systemTag = false;

    /** 활성화 여부 (false면 게시판/드라이브 필터에서 제외) */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

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
