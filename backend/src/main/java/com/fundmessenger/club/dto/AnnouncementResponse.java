package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.Announcement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter @Builder
public class AnnouncementResponse {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime eventDate;
    private boolean pinned;
    private boolean banner;
    private int pinOrder;
    private Long linkedEventId;
    private List<Integer> reminderDaysBefore;
    private String createdByName;
    private LocalDateTime createdAt;

    public static AnnouncementResponse from(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .eventDate(a.getEventDate())
                .pinned(a.isPinned())
                .banner(a.isBanner())
                .pinOrder(a.getPinOrder())
                .linkedEventId(a.getLinkedEvent() != null ? a.getLinkedEvent().getId() : null)
                .reminderDaysBefore(parseReminderDays(a.getReminderDaysBefore()))
                .createdByName(a.getCreatedBy().getName())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private static List<Integer> parseReminderDays(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}
