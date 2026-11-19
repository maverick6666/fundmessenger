package com.fundmessenger.club.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class CreateAnnouncementRequest {
    private String title;
    private String content;
    private LocalDateTime eventDate;
    private boolean pinned;
    private boolean banner;
    private List<Integer> reminderDaysBefore;
}
