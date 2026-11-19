package com.fundmessenger.club.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carousel_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "club_membership_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CarouselMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_membership_id", nullable = false)
    private ClubMembership clubMembership;

    /** 역할명: 회장, 부회장, 총무, 커스텀 등 */
    @Column(nullable = false)
    private String roleName;

    /** 캐러셀 전용 소개글 (마이페이지 bio와 별개) */
    @Column(length = 200)
    private String bio;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
