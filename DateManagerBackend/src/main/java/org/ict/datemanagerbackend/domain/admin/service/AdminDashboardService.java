package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Response.DashboardStatsDto;

public interface AdminDashboardService {
  // <<관리자 홈 대시보드용 통계 + 최근 7일 방문자/구독 증가 추이 조회>>
  DashboardStatsDto getStats();
}
