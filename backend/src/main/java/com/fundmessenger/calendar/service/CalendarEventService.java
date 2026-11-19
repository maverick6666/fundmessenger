package com.fundmessenger.calendar.service;

import com.fundmessenger.calendar.dto.CalendarEventResponse;
import com.fundmessenger.calendar.dto.CreateCalendarEventRequest;
import com.fundmessenger.calendar.dto.UpdateCalendarEventRequest;
import com.fundmessenger.calendar.entity.CalendarEvent;
import com.fundmessenger.calendar.repository.CalendarEventRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.config.InputSanitizer;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final ClubAuthorizationService authService;

    /** 다가오는 일정 (오늘 이후, limit개) — 동아리 전체 */
    public List<CalendarEventResponse> getUpcomingEvents(Long clubId, int limit, Long userId) {
        authService.validateClubMember(userId, clubId);
        return calendarEventRepository
                .findUpcomingByClubId(clubId, LocalDate.now(), PageRequest.of(0, limit))
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 다가오는 일정 — 팀 스코프 (동아리 전체 + 해당 팀) */
    public List<CalendarEventResponse> getUpcomingEvents(Long clubId, Long teamId, int limit, Long userId) {
        authService.validateClubMember(userId, clubId);
        return calendarEventRepository
                .findUpcomingByClubIdAndTeamId(clubId, teamId, LocalDate.now(), PageRequest.of(0, limit))
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 월간 뷰: 해당 월의 모든 일정 — 동아리 전체 */
    public List<CalendarEventResponse> getEventsByMonth(Long clubId, int year, int month, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateYearMonth(year, month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return calendarEventRepository
                .findByClubIdAndDateRange(clubId, startDate, endDate)
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 월간 뷰 — 팀 스코프 */
    public List<CalendarEventResponse> getEventsByMonth(Long clubId, Long teamId, int year, int month, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateYearMonth(year, month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return calendarEventRepository
                .findByClubIdAndTeamIdAndDateRange(clubId, teamId, startDate, endDate)
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 다가오는 일정 — 동아리 전체만 (team IS NULL) */
    public List<CalendarEventResponse> getUpcomingEventsClubOnly(Long clubId, int limit, Long userId) {
        authService.validateClubMember(userId, clubId);
        return calendarEventRepository
                .findUpcomingByClubIdClubOnly(clubId, LocalDate.now(), PageRequest.of(0, limit))
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 월간 뷰 — 동아리 전체만 (team IS NULL) */
    public List<CalendarEventResponse> getEventsByMonthClubOnly(Long clubId, int year, int month, Long userId) {
        authService.validateClubMember(userId, clubId);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return calendarEventRepository
                .findByClubIdAndDateRangeClubOnly(clubId, startDate, endDate)
                .stream()
                .map(CalendarEventResponse::from)
                .collect(Collectors.toList());
    }

    /** 일정 생성 (동아리: 임원급, 팀: 팀장+임원급) */
    @Transactional
    public CalendarEventResponse createEvent(Long clubId, CreateCalendarEventRequest request, Long userId) {
        ClubMembership membership = request.getTeamId() != null
                ? authService.validateTeamManager(userId, clubId, request.getTeamId())
                : authService.validateClubAdmin(userId, clubId);
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("동아리를 찾을 수 없습니다"));

        Team team = null;
        if (request.getTeamId() != null) {
            team = teamRepository.findById(request.getTeamId())
                    .orElseThrow(() -> new NotFoundException("팀을 찾을 수 없습니다"));
            if (!team.getClub().getId().equals(clubId)) {
                throw new ForbiddenException("해당 동아리의 팀이 아닙니다");
            }
        }

        CalendarEvent event = CalendarEvent.builder()
                .club(club)
                .team(team)
                .title(InputSanitizer.sanitizePlainText(request.getTitle()))
                .eventDate(request.getEventDate())
                .eventTime(request.getEventTime())
                .description(InputSanitizer.sanitizePlainText(request.getDescription()))
                .createdBy(membership)
                .build();

        return CalendarEventResponse.from(calendarEventRepository.save(event));
    }

    /** 일정 수정 (동아리: 임원급, 팀: 팀장+임원급) */
    @Transactional
    public CalendarEventResponse updateEvent(Long clubId, Long eventId, UpdateCalendarEventRequest request, Long userId) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("일정을 찾을 수 없습니다"));

        if (!event.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 일정이 아닙니다");
        }

        if (event.getTeam() != null) {
            authService.validateTeamManager(userId, clubId, event.getTeam().getId());
        } else {
            authService.validateClubAdmin(userId, clubId);
        }

        event.setTitle(InputSanitizer.sanitizePlainText(request.getTitle()));
        event.setEventDate(request.getEventDate());
        event.setEventTime(request.getEventTime());
        event.setDescription(InputSanitizer.sanitizePlainText(request.getDescription()));

        return CalendarEventResponse.from(event);
    }

    /** 일정 삭제 (동아리: 임원급, 팀: 팀장+임원급) */
    @Transactional
    public void deleteEvent(Long clubId, Long eventId, Long userId) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("일정을 찾을 수 없습니다"));

        if (!event.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 일정이 아닙니다");
        }

        if (event.getTeam() != null) {
            authService.validateTeamManager(userId, clubId, event.getTeam().getId());
        } else {
            authService.validateClubAdmin(userId, clubId);
        }

        calendarEventRepository.delete(event);
    }

    private void validateYearMonth(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("유효하지 않은 연도입니다: " + year);
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("유효하지 않은 월입니다: " + month);
        }
    }
}
