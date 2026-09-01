package org.ict.datemanagerbackend.domain.financialplanner.dto;

// PATCH /api/v1/planner/goal/{goalId}/progress 요청 바디 - 현재 모인 금액 수동 업데이트.
public record ProgressUpdateRequestDto(Long currentAmount) {
}
