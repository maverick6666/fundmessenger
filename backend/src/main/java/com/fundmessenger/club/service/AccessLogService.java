package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.AttendanceStatsResponse;
import com.fundmessenger.club.entity.AccessLog;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.AccessLogRepository;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final UserRepository userRepository;
    private final ClubAuthorizationService authService;

    /**
     * 오늘 접속 기록 저장 (이미 있으면 skip)
     * 클럽 진입 시 1회 호출
     */
    @Transactional
    public void recordAccess(Long userId, Long clubId) {
        LocalDate today = LocalDate.now();
        if (accessLogRepository.existsByUserIdAndClubIdAndDate(userId, clubId, today)) {
            return; // 이미 기록됨
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        accessLogRepository.save(AccessLog.builder()
                .user(user)
                .club(club)
                .date(today)
                .build());
    }

    /**
     * 특정 월의 접속일 배열 반환
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getAccessDates(Long clubId, int year, int month, Long userId) {
        authService.validateClubMember(userId, clubId);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        return accessLogRepository.findDatesByUserIdAndClubIdAndDateBetween(
                userId, clubId, startDate, endDate);
    }

    /**
     * 접속률 통계 (관리자용)
     */
    @Transactional(readOnly = true)
    public AttendanceStatsResponse getAttendanceStats(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        LocalDate today = LocalDate.now();

        // 활성 멤버 목록 (봇 제외)
        List<ClubMembership> activeMembers = clubMembershipRepository
                .findByClubIdAndStatus(clubId, MemberStatus.APPROVED)
                .stream()
                .filter(m -> !m.isBot())
                .toList();
        int totalMembers = activeMembers.size();
        if (totalMembers == 0) {
            return AttendanceStatsResponse.builder()
                    .weeklyRate(0)
                    .monthlyRate(0)
                    .memberStats(List.of())
                    .build();
        }

        // 이번 주 (월~일)
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        long weeklyAccessCount = accessLogRepository
                .countDistinctUsersByClubIdAndDateBetween(clubId, weekStart, today);
        double weeklyRate = Math.round((double) weeklyAccessCount / totalMembers * 1000) / 10.0;

        // 이번 달
        LocalDate monthStart = today.withDayOfMonth(1);
        long monthlyAccessCount = accessLogRepository
                .countDistinctUsersByClubIdAndDateBetween(clubId, monthStart, today);
        double monthlyRate = Math.round((double) monthlyAccessCount / totalMembers * 1000) / 10.0;

        // 멤버별 이번 달 접속일 수
        int totalDays = (int) ChronoUnit.DAYS.between(monthStart, today) + 1;
        List<Object[]> accessCounts = accessLogRepository
                .countAccessDaysByMember(clubId, monthStart, today);
        Map<Long, Long> accessMap = accessCounts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<AttendanceStatsResponse.MemberStat> memberStats = activeMembers.stream()
                .map(m -> AttendanceStatsResponse.MemberStat.builder()
                        .memberName(m.getName())
                        .accessDays(accessMap.getOrDefault(m.getUser().getId(), 0L).intValue())
                        .totalDays(totalDays)
                        .build())
                .sorted((a, b) -> Integer.compare(b.getAccessDays(), a.getAccessDays()))
                .toList();

        return AttendanceStatsResponse.builder()
                .weeklyRate(weeklyRate)
                .monthlyRate(monthlyRate)
                .memberStats(memberStats)
                .build();
    }
}
