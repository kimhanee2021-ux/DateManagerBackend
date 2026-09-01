package org.ict.datemanagerbackend.domain.financialplanner.dto;

import java.util.List;

// GET /api/v1/planner/dashboard 응답. goal이 null이면 아직 목표가 없는 Empty State(개발 명세서
// 4-2). exchangeBriefing은 목적지 통화 매핑에 성공했을 때만 채워지고, 실패/없음이면 null이라
// 프론트가 그 블록을 아예 렌더링하지 않는다.
public record DashboardResponseDto(
    GoalResultDto goal,
    String aiComment,
    ExchangeBriefingDto exchangeBriefing,
    List<RecommendedProductDto> recommendedProducts
) {
}
