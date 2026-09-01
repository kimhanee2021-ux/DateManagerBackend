package org.ict.datemanagerbackend.domain.financialplanner.dto;

// 목표 생성/수정(POST /goal) 및 대시보드 응답에 공통으로 쓰는 목표 정보. needsClarification이
// true면 question만 채워지고 나머지는 전부 null(개발 명세서 4-2 "Clarification 필요" 상태).
public record GoalResultDto(
    boolean needsClarification,
    String question,
    Long goalId,
    String title,
    Long targetAmount,
    Long currentAmount,
    Integer targetPeriodMonth,
    String category,
    String destinationCountry,
    String ownerType // "SOLO" | "COUPLE" - 프론트가 "나의 목표"/"우리의 목표" 문구만 분기
) {
}
