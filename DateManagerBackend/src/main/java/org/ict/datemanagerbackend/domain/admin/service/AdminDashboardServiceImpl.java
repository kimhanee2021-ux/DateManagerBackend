package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Response.DailyCountDto;
import org.ict.datemanagerbackend.domain.admin.dto.Response.DashboardStatsDto;
import org.ict.datemanagerbackend.domain.admin.dto.Response.GenderBreakdownDto;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
import org.ict.datemanagerbackend.domain.user.entity.LoginLog;
import org.ict.datemanagerbackend.domain.user.repository.LoginLogRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 방문자는 LoginLog(로그인 성공마다 기록됨)를, 구독 증가는 Subscription.startedAt을 날짜별로 묶어서 센다.
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

  private final UserRepository userRepository;
  private final CoupleRepository coupleRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final LoginLogRepository loginLogRepository;

  public AdminDashboardServiceImpl(UserRepository userRepository, CoupleRepository coupleRepository,
                                    SubscriptionRepository subscriptionRepository, LoginLogRepository loginLogRepository) {
    this.userRepository = userRepository;
    this.coupleRepository = coupleRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.loginLogRepository = loginLogRepository;
  }

  @Override
  public DashboardStatsDto getStats() {
    LocalDate today = LocalDate.now();
    LocalDateTime windowStart = today.minusDays(6).atStartOfDay();

    long totalUsers = userRepository.countByWithdrawnAtIsNull();
    long totalSubscribers = subscriptionRepository.countByStatus("ACTIVE");
    long totalCouples = coupleRepository.count();

    Map<LocalDate, Set<Long>> visitorsByDay = new HashMap<>();
    for (LoginLog log : loginLogRepository.findByLoggedInAtAfter(windowStart)) {
      LocalDate day = log.getLoggedInAt().toLocalDate();
      visitorsByDay.computeIfAbsent(day, d -> new HashSet<>()).add(log.getUser().getId());
    }
    long todayVisitors = visitorsByDay.getOrDefault(today, Set.of()).size();
    List<DailyCountDto> visitorTrend = buildTrend(today, day -> (long) visitorsByDay.getOrDefault(day, Set.of()).size());

    Map<LocalDate, Long> subsByDay = subscriptionRepository.findByStartedAtAfter(windowStart).stream()
        .filter(s -> s.getStartedAt() != null)
        .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate(), Collectors.counting()));
    List<DailyCountDto> subscriptionTrend = buildTrend(today, day -> subsByDay.getOrDefault(day, 0L));

    GenderBreakdownDto genderBreakdown = new GenderBreakdownDto(
        userRepository.countByWithdrawnAtIsNullAndGender("MALE"),
        userRepository.countByWithdrawnAtIsNullAndGender("FEMALE"),
        userRepository.countByWithdrawnAtIsNullAndGender("UNKNOWN"));

    return new DashboardStatsDto(totalUsers, totalSubscribers, totalCouples, todayVisitors,
        visitorTrend, subscriptionTrend, genderBreakdown);
  }

  private List<DailyCountDto> buildTrend(LocalDate endDay, Function<LocalDate, Long> counter) {
    List<DailyCountDto> trend = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate day = endDay.minusDays(i);
      trend.add(new DailyCountDto(day.toString(), counter.apply(day)));
    }
    return trend;
  }
}
