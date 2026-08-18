package org.ict.datemanagerbackend.domain.admin.dto.Response;

import java.util.List;

public record DashboardStatsDto(long totalUsers, long totalSubscribers, long totalCouples, long todayVisitors,
                                 List<DailyCountDto> visitorTrend, List<DailyCountDto> subscriptionTrend,
                                 GenderBreakdownDto genderBreakdown) {
}
