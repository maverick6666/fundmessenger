package com.fundmessenger.calendar.dto;

import com.fundmessenger.calendar.entity.CalendarEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class CalendarEventResponse {
    private Long id;
    private String title;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String description;
    private Long teamId;
    private String teamName;
    private String createdByName;
    private Long createdByMembershipId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CalendarEventResponse from(CalendarEvent event) {
        return CalendarEventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .eventDate(event.getEventDate())
                .eventTime(event.getEventTime())
                .description(event.getDescription())
                .teamId(event.getTeam() != null ? event.getTeam().getId() : null)
                .teamName(event.getTeam() != null ? event.getTeam().getName() : null)
                .createdByName(event.getCreatedBy().getName())
                .createdByMembershipId(event.getCreatedBy().getId())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
