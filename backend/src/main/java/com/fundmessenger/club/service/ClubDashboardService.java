package com.fundmessenger.club.service;

import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.dto.ClubDashboardResponse;
import com.fundmessenger.club.dto.ClubDashboardResponse.*;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.*;
import com.fundmessenger.drive.repository.DriveFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubDashboardService {

    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final AccessLogRepository accessLogRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final DriveFileRepository driveFileRepository;
    private final BoardRepository boardRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;

    public ClubDashboardResponse getDashboard(Long clubId, Long userId, String period) {
        clubAuthorizationService.validateClubAdmin(userId, clubId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        LocalDateTime startDate = resolveStartDate(period);
        LocalDate today = LocalDate.now();

        return new ClubDashboardResponse(
                buildMemberPanel(clubId, today),
                buildAccessPanel(clubId, today, startDate),
                buildCreditPanel(club, clubId),
                buildContentPanel(clubId, startDate)
        );
    }

    // ========== Period Resolution ==========

    private LocalDateTime resolveStartDate(String period) {
        if (period == null) period = "MONTH";
        return switch (period.toUpperCase()) {
            case "WEEK" -> LocalDateTime.now().minusDays(7);
            case "MONTH" -> LocalDateTime.now().minusDays(30);
            case "SEMESTER" -> LocalDateTime.now().minusDays(180);
            case "ALL" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> LocalDateTime.now().minusDays(30);
        };
    }

    // ========== Member Panel ==========

    private MemberPanel buildMemberPanel(Long clubId, LocalDate today) {
        int total = (int) membershipRepository.countByClubIdAndStatus(clubId, MemberStatus.APPROVED);
        int pending = (int) membershipRepository.countByClubIdAndStatus(clubId, MemberStatus.PENDING);

        // Active = accessed in last 30 days
        LocalDate monthAgo = today.minusDays(30);
        int active = (int) accessLogRepository.countDistinctUsersByClubIdAndDateBetween(clubId, monthAgo, today);
        int inactive = Math.max(0, total - active);

        // New members this month
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        int newThisMonth = (int) membershipRepository.countNewApprovedSince(clubId, startOfMonth);

        // Generation distribution from M:N generations
        List<NameCount> genDist = buildGenerationDistribution(clubId);

        // Monthly signups (last 6 months)
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<DateCount> monthlySignups = membershipRepository.findMonthlySignups(clubId, sixMonthsAgo)
                .stream()
                .map(row -> new DateCount((String) row[0], (Long) row[1]))
                .toList();

        return new MemberPanel(total, active, inactive, newThisMonth, pending, genDist, monthlySignups);
    }

    private List<NameCount> buildGenerationDistribution(Long clubId) {
        List<ClubMembership> members = membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED);
        Map<String, Long> genMap = new HashMap<>();
        int noGen = 0;
        for (var m : members) {
            if (m.getGenerations() == null || m.getGenerations().isEmpty()) {
                noGen++;
            } else {
                for (var g : m.getGenerations()) {
                    genMap.merge(g.getName(), 1L, Long::sum);
                }
            }
        }
        if (noGen > 0) genMap.put("미지정", (long) noGen);
        return genMap.entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(NameCount::name))
                .toList();
    }

    // ========== Access Panel ==========

    private AccessPanel buildAccessPanel(Long clubId, LocalDate today, LocalDateTime startDate) {
        int total = (int) membershipRepository.countByClubIdAndStatus(clubId, MemberStatus.APPROVED);

        LocalDate weekAgo = today.minusDays(7);
        LocalDate monthAgo = today.minusDays(30);

        long weeklyVisitors = accessLogRepository.countDistinctUsersByClubIdAndDateBetween(clubId, weekAgo, today);
        long monthlyVisitors = accessLogRepository.countDistinctUsersByClubIdAndDateBetween(clubId, monthAgo, today);

        double weeklyRate = total > 0 ? Math.round(weeklyVisitors * 1000.0 / total) / 10.0 : 0;
        double monthlyRate = total > 0 ? Math.round(monthlyVisitors * 1000.0 / total) / 10.0 : 0;

        int todayVisitors = (int) accessLogRepository.countDistinctUsersByClubIdAndDate(clubId, today);

        // DAU line chart (last 30 days)
        List<DateCount> dailyActiveUsers = accessLogRepository
                .findDailyAccessCounts(clubId, monthAgo, today)
                .stream()
                .map(row -> new DateCount(row[0].toString(), (Long) row[1]))
                .toList();

        // Team access rates
        List<NameRate> teamAccessRates = buildTeamAccessRates(clubId, monthAgo, today);

        return new AccessPanel(weeklyRate, monthlyRate, todayVisitors, dailyActiveUsers, teamAccessRates);
    }

    private List<NameRate> buildTeamAccessRates(Long clubId, LocalDate startDate, LocalDate endDate) {
        List<Team> teams = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId);
        if (teams.isEmpty()) return List.of();

        // Get team membership mapping
        List<MemberTag> teamTags = memberTagRepository.findByClubIdAndTagTypeIn(
                clubId, List.of(TagType.TEAM_LEADER, TagType.TEAM_MEMBER, TagType.TEAM_MANAGER));

        // Group userId by teamId
        Map<Long, Set<Long>> teamUserIds = new HashMap<>();
        for (var tag : teamTags) {
            if (tag.getTeam() != null) {
                teamUserIds.computeIfAbsent(tag.getTeam().getId(), k -> new HashSet<>())
                        .add(tag.getClubMembership().getUser().getId());
            }
        }

        // Get access days per member
        Map<Long, Long> accessDaysByUser = new HashMap<>();
        for (var row : accessLogRepository.countAccessDaysByMember(clubId, startDate, endDate)) {
            accessDaysByUser.put((Long) row[0], (Long) row[1]);
        }

        List<NameRate> rates = new ArrayList<>();
        for (var team : teams) {
            Set<Long> userIds = teamUserIds.getOrDefault(team.getId(), Set.of());
            if (userIds.isEmpty()) {
                rates.add(new NameRate(team.getName(), 0));
                continue;
            }
            long accessed = userIds.stream().filter(uid -> accessDaysByUser.containsKey(uid)).count();
            double rate = Math.round(accessed * 1000.0 / userIds.size()) / 10.0;
            rates.add(new NameRate(team.getName(), rate));
        }
        return rates;
    }

    // ========== Credit Panel ==========

    private CreditPanel buildCreditPanel(Club club, Long clubId) {
        int balance = club.getCreditBalance() != null ? club.getCreditBalance() : 0;

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int grantedThisMonth = (int) creditHistoryRepository
                .sumAmountByClubIdAndTypeAndCreatedAtAfter(clubId, CreditHistory.CreditType.GRANT, startOfMonth);
        int usedThisMonth = (int) creditHistoryRepository
                .sumAmountByClubIdAndTypeAndCreatedAtAfter(clubId, CreditHistory.CreditType.DEDUCT, startOfMonth);

        // Monthly credit flow (last 6 months)
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Object[]> rawFlow = creditHistoryRepository.findMonthlyCreditFlow(clubId, sixMonthsAgo);
        Map<String, long[]> monthMap = new LinkedHashMap<>(); // [grant, deduct, allocate]
        for (var row : rawFlow) {
            String month = (String) row[0];
            CreditHistory.CreditType type = (CreditHistory.CreditType) row[1];
            long amount = ((Number) row[2]).longValue();
            long[] vals = monthMap.computeIfAbsent(month, k -> new long[3]);
            switch (type) {
                case GRANT -> vals[0] += amount;
                case DEDUCT -> vals[1] += amount;
                case ALLOCATE -> vals[2] += amount;
                default -> {} // REVOKE, RECLAIM not shown separately
            }
        }
        List<MonthlyCredit> monthlyCreditFlow = monthMap.entrySet().stream()
                .map(e -> new MonthlyCredit(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        // Type distribution
        List<NameCount> typeDistribution = creditHistoryRepository.findTypeDistribution(clubId)
                .stream()
                .map(row -> new NameCount(((CreditHistory.CreditType) row[0]).name(), ((Number) row[1]).longValue()))
                .toList();

        return new CreditPanel(balance, grantedThisMonth, usedThisMonth, monthlyCreditFlow, typeDistribution);
    }

    // ========== Content Panel ==========

    private ContentPanel buildContentPanel(Long clubId, LocalDateTime startDate) {
        long posts = postRepository.countByClubIdAndCreatedAtAfter(clubId, startDate);
        long comments = postCommentRepository.countByClubIdAndCreatedAtAfter(clubId, startDate);
        long files = driveFileRepository.countByClubIdAndCreatedAtAfter(clubId, startDate);
        long likes = postLikeRepository.countByClubIdAndCreatedAtAfter(clubId, startDate);

        // Weekly activity (posts + comments merged)
        List<Object[]> weeklyPosts = postRepository.findWeeklyPostCounts(clubId, startDate);
        List<Object[]> weeklyComments = postCommentRepository.findWeeklyCommentCounts(clubId, startDate);

        Map<String, long[]> weeklyMap = new LinkedHashMap<>(); // [posts, comments]
        for (var row : weeklyPosts) {
            String week = (String) row[0];
            long count = ((Number) row[1]).longValue();
            weeklyMap.computeIfAbsent(week, k -> new long[2])[0] = count;
        }
        for (var row : weeklyComments) {
            String week = (String) row[0];
            long count = ((Number) row[1]).longValue();
            weeklyMap.computeIfAbsent(week, k -> new long[2])[1] = count;
        }
        List<WeeklyContent> weeklyActivity = weeklyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new WeeklyContent(e.getKey(), e.getValue()[0], e.getValue()[1], 0))
                .toList();

        // Board post counts
        List<Object[]> boardCounts = postRepository.countPostsByClubBoards(clubId);
        Map<Long, Long> boardCountMap = new HashMap<>();
        for (var row : boardCounts) {
            boardCountMap.put((Long) row[0], (Long) row[1]);
        }
        var boards = boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId);
        List<NameCount> boardPostCounts = boards.stream()
                .map(b -> new NameCount(b.getName(), boardCountMap.getOrDefault(b.getId(), 0L)))
                .toList();

        return new ContentPanel(posts, comments, files, likes, weeklyActivity, boardPostCounts);
    }
}
