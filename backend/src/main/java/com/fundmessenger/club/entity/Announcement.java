package com.fundmessenger.club.entity;

import com.fundmessenger.calendar.entity.CalendarEvent;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_announcement_club", columnList = "club_id, is_pinned DESC, created_at DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime eventDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_event_id")
    private CalendarEvent linkedEvent;

    @Column(length = 50)
    private String reminderDaysBefore;

    @Builder.Default
    private boolean isPinned = false;

    @Builder.Default
    private boolean isBanner = false;

    @Builder.Default
    private int pinOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private ClubMembership createdBy;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
