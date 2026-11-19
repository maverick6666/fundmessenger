package com.fundmessenger.calendar.repository;

import com.fundmessenger.calendar.entity.CalendarEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /** 다가오는 일정 (오늘 이후, eventDate ASC → eventTime ASC) — FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND e.eventDate >= :today " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findUpcomingByClubId(
            @Param("clubId") Long clubId,
            @Param("today") LocalDate today,
            Pageable pageable);

    /** 월간 뷰: 특정 날짜 범위의 일정 — FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND e.eventDate >= :startDate AND e.eventDate <= :endDate " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findByClubIdAndDateRange(
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 팀 스코프 다가오는 일정: 동아리 전체(team=null) + 해당 팀 일정 — FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND (e.team IS NULL OR e.team.id = :teamId) " +
           "AND e.eventDate >= :today " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findUpcomingByClubIdAndTeamId(
            @Param("clubId") Long clubId,
            @Param("teamId") Long teamId,
            @Param("today") LocalDate today,
            Pageable pageable);

    /** 팀 스코프 월간 뷰 — FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND (e.team IS NULL OR e.team.id = :teamId) " +
           "AND e.eventDate >= :startDate AND e.eventDate <= :endDate " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findByClubIdAndTeamIdAndDateRange(
            @Param("clubId") Long clubId,
            @Param("teamId") Long teamId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 동아리 전체 일정만 (team IS NULL) — 다가오는, FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND e.team IS NULL " +
           "AND e.eventDate >= :today " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findUpcomingByClubIdClubOnly(
            @Param("clubId") Long clubId,
            @Param("today") LocalDate today,
            Pageable pageable);

    /** 동아리 전체 일정만 (team IS NULL) — 월간, FETCH JOIN으로 N+1 방지 */
    @Query("SELECT e FROM CalendarEvent e " +
           "LEFT JOIN FETCH e.team LEFT JOIN FETCH e.createdBy cb LEFT JOIN FETCH cb.user " +
           "WHERE e.club.id = :clubId " +
           "AND e.team IS NULL " +
           "AND e.eventDate >= :startDate AND e.eventDate <= :endDate " +
           "ORDER BY e.eventDate ASC, e.eventTime ASC NULLS LAST")
    List<CalendarEvent> findByClubIdAndDateRangeClubOnly(
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
