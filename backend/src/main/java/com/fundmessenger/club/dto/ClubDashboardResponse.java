package com.fundmessenger.club.dto;

import java.util.List;

public record ClubDashboardResponse(
        MemberPanel member,
        AccessPanel access,
        CreditPanel credit,
        ContentPanel content
) {
    public record MemberPanel(
            int total,
            int active,
            int inactive,
            int newThisMonth,
            int pending,
            List<NameCount> generationDistribution,
            List<DateCount> monthlySignups
    ) {}

    public record AccessPanel(
            double weeklyRate,
            double monthlyRate,
            int todayVisitors,
            List<DateCount> dailyActiveUsers,
            List<NameRate> teamAccessRates
    ) {}

    public record CreditPanel(
            int balance,
            int grantedThisMonth,
            int usedThisMonth,
            List<MonthlyCredit> monthlyCreditFlow,
            List<NameCount> typeDistribution
    ) {}

    public record ContentPanel(
            long posts,
            long comments,
            long files,
            long likes,
            List<WeeklyContent> weeklyActivity,
            List<NameCount> boardPostCounts
    ) {}

    public record NameCount(String name, long count) {}
    public record DateCount(String date, long count) {}
    public record NameRate(String name, double rate) {}
    public record MonthlyCredit(String month, long grant, long deduct, long allocate) {}
    public record WeeklyContent(String week, long posts, long comments, long files) {}
}
