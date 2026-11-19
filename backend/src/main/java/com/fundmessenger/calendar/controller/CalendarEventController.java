package com.fundmessenger.calendar.controller;

import com.fundmessenger.calendar.dto.CalendarEventResponse;
import com.fundmessenger.calendar.dto.CreateCalendarEventRequest;
import com.fundmessenger.calendar.dto.UpdateCalendarEventRequest;
import com.fundmessenger.calendar.service.CalendarEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/calendar")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    /** 다가오는 일정 (오늘 이후, limit개) — 멤버. teamId/clubOnly 필터 지원 */
    @GetMapping("/events/upcoming")
    public ResponseEntity<List<CalendarEventResponse>> getUpcomingEvents(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false, defaultValue = "false") boolean clubOnly,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (clubOnly) {
            return ResponseEntity.ok(calendarEventService.getUpcomingEventsClubOnly(clubId, limit, userId));
        }
        if (teamId != null) {
            return ResponseEntity.ok(calendarEventService.getUpcomingEvents(clubId, teamId, limit, userId));
        }
        return ResponseEntity.ok(calendarEventService.getUpcomingEvents(clubId, limit, userId));
    }

    /** 월간 뷰: 해당 월의 모든 일정 — 멤버. teamId/clubOnly 필터 지원 */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventResponse>> getEventsByMonth(
            @PathVariable Long clubId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false, defaultValue = "false") boolean clubOnly,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (clubOnly) {
            return ResponseEntity.ok(calendarEventService.getEventsByMonthClubOnly(clubId, year, month, userId));
        }
        if (teamId != null) {
            return ResponseEntity.ok(calendarEventService.getEventsByMonth(clubId, teamId, year, month, userId));
        }
        return ResponseEntity.ok(calendarEventService.getEventsByMonth(clubId, year, month, userId));
    }

    /** 일정 생성 — 임원급 또는 팀장 */
    @PostMapping("/events")
    public ResponseEntity<CalendarEventResponse> createEvent(
            @PathVariable Long clubId,
            @RequestBody @Valid CreateCalendarEventRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(calendarEventService.createEvent(clubId, request, userId));
    }

    /** 일정 수정 — 임원급 또는 팀장 */
    @PutMapping("/events/{eventId}")
    public ResponseEntity<CalendarEventResponse> updateEvent(
            @PathVariable Long clubId,
            @PathVariable Long eventId,
            @RequestBody @Valid UpdateCalendarEventRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(calendarEventService.updateEvent(clubId, eventId, request, userId));
    }

    /** 일정 삭제 — 임원급 또는 팀장 */
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long clubId,
            @PathVariable Long eventId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        calendarEventService.deleteEvent(clubId, eventId, userId);
        return ResponseEntity.noContent().build();
    }
}
