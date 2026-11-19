package com.fundmessenger.club.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 어드민 폴더 — 운영 설정 탭의 리스트 아이템을 그룹핑.
 * 아이폰 폴더 패턴: 아이템 위에 아이템 드래그 → 폴더 자동 생성.
 */
@Entity
@Table(name = "admin_folders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 100)
    private String name;

    /** 폴더 유형: TAG, BOARD, CATEGORY, GENERATION */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminFolderType folderType;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /** 접힘 상태 (true면 접힌 상태) */
    @Column(nullable = false)
    @Builder.Default
    private boolean collapsed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
