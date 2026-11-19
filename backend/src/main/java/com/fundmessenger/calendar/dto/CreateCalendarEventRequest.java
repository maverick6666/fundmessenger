package com.fundmessenger.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class CreateCalendarEventRequest {

    @NotBlank(message = "일정 제목은 필수입니다")
    @Size(max = 100)
    private String title;

    @NotNull(message = "일정 날짜는 필수입니다")
    private LocalDate eventDate;

    /** null이면 종일 일정 */
    private LocalTime eventTime;

    @Size(max = 500)
    private String description;

    /** null이면 동아리 전체 일정, 값이 있으면 팀 일정 */
    private Long teamId;
}
