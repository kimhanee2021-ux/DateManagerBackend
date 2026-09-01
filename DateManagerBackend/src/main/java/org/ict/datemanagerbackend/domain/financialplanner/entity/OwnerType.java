package org.ict.datemanagerbackend.domain.financialplanner.entity;

// FundGoal이 개인 소유인지 커플 공동인지 - 커플 연결 여부에 따라 FinancialPlannerServiceImpl이
// 자동으로 정하고, 유저가 직접 고르는 값이 아니다(원본 기획 의도인 "수동 분기 없는 자동 처리").
public enum OwnerType {
  SOLO,
  COUPLE
}
