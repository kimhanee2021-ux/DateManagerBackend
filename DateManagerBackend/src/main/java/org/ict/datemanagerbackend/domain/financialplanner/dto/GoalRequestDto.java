package org.ict.datemanagerbackend.domain.financialplanner.dto;

// POST /api/v1/planner/goal 요청 바디 - 자연어 입력 한 줄. 목표를 새로 만들 때도, 기존 목표를
// 수정할 때도 같은 엔드포인트/바디를 쓴다(FundGoal은 owner당 1개만 유지, 2026-08-31 결정).
public record GoalRequestDto(String text) {
}
