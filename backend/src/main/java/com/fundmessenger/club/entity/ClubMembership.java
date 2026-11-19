package com.fundmessenger.club.entity;

import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "club_memberships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "club_id"}),
       indexes = {
           @Index(name = "idx_membership_club_status", columnList = "club_id, status")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** 실명 */
    @Column(nullable = false)
    private String name;

    /** 학번 */
    @Column(nullable = false)
    private String studentId;

    /** 전화번호 */
    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime approvedAt;

    /** 임원급 권한 (관리탭 접근 등) — 태그가 아닌 직접 부여 */
    @Column(nullable = false)
    @Builder.Default
    private boolean executive = false;

    /** AI 봇 가상 멤버십 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean bot = false;

    /** 기수 태그 누적 (아카이브 멤버 조회용) */
    @ManyToMany
    @JoinTable(
        name = "membership_generations",
        joinColumns = @JoinColumn(name = "club_membership_id"),
        inverseJoinColumns = @JoinColumn(name = "semi_category_id")
    )
    @Builder.Default
    private Set<SemiCategory> generations = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        this.appliedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = MemberStatus.PENDING;
        }
    }
}
