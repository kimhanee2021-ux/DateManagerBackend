package org.ict.datemanagerbackend.domain.aichat.dto;

// AI 스마트 자금 플래너 "자연어 목표 입력" 파싱 결과(2026-08-31). needsClarification이 true면
// title 이하 필드는 전부 null이고 question만 채워진다 - 컨트롤러가 그대로 프론트에 내려줘서
// 되묻는 채팅형 UI를 그린다(개발 명세서 4-2).
public record FundGoalParseDto(
    boolean needsClarification,
    String question,
    String title,
    Long targetAmount,
    Integer targetPeriodMonth,
    String category,
    String destinationCountry
) {
}
