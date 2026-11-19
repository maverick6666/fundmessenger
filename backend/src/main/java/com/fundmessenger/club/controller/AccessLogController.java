package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.AttendanceStatsResponse;
import com.fundmessenger.club.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/access-log")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    /** 해당 월의 접속일 배열 반환 */
    @GetMapping
    public ResponseEntity<List<LocalDate>> getAccessDates(
            @PathVariable Long clubId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(accessLogService.getAccessDates(clubId, year, month, userId));
    }

    /** 접속 기록 (클럽 진입 시 프론트에서 호출) */
    @PostMapping
    public ResponseEntity<Void> recordAccess(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        accessLogService.recordAccess(userId, clubId);
        return ResponseEntity.ok().build();
    }

    /** 접속률 통계 (관리자 전용) */
    @GetMapping("/stats")
    public ResponseEntity<AttendanceStatsResponse> getAttendanceStats(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(accessLogService.getAttendanceStats(clubId, userId));
    }
}
