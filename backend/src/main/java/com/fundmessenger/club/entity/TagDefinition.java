package com.fundmessenger.club.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 태그 정의 — 동아리의 태그 유형 메타데이터.
 * MemberTag는 멤버에게 부여된 태그 인스턴스, TagDefinition은 태그 유형 자체.
 */
@Entity
@Table(name = "tag_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "name"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TagDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType tagType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionLevel permissionLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

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
