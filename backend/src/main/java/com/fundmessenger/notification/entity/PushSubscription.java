package com.fundmessenger.notification.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = "endpoint"),
        indexes = @Index(name = "idx_push_sub_user_club", columnList = "user_id, club_id"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(length = 500, nullable = false, unique = true)
    private String endpoint;

    @Column(name = "p256dh", length = 200, nullable = false)
    private String p256dh;

    @Column(name = "auth_key", length = 100, nullable = false)
    private String authKey;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
