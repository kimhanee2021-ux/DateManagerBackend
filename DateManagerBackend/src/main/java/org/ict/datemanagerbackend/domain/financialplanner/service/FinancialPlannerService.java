package org.ict.datemanagerbackend.domain.financialplanner.service;

import org.ict.datemanagerbackend.domain.financialplanner.dto.DashboardResponseDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.GoalResultDto;
import org.ict.datemanagerbackend.domain.user.entity.User;

public interface FinancialPlannerService {
  // <<자연어 목표 생성/수정 - owner당 목표는 1개뿐이라 이미 있으면 덮어쓴다>>
  GoalResultDto createOrUpdateGoal(User user, String text);
  // <<목표 현황 + AI 브리핑 + 환율 브리핑(목적지 매핑 성공 시만) + 추천 상품 리스트>>
  DashboardResponseDto getDashboard(User user);
  // <<현재 모인 금액 수동 업데이트>>
  GoalResultDto updateProgress(User user, Long goalId, Long currentAmount);
}
