package com.fundmessenger.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProvider socialProvider;

    @Column(nullable = false)
    private String socialProviderId;

    /** 시스템 슈퍼어드민 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean superAdmin = false;

    /** 프로필 필드 */
    @Column(length = 100)
    private String bio;  // 한줄 소개

    @Column(length = 30)
    private String phone;  // 연락처

    private String avatarUrl;  // 프로필 사진 URL (/api/files/images/xxx)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** MAU 추적: 마지막 활동 시간 (로그인 시 갱신) */
    private LocalDateTime lastActiveAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
    }
}
