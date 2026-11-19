package com.fundmessenger.notification.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_settings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"club_id", "user_id", "type", "team_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private NotificationType type;

    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
