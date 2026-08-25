package org.ict.datemanagerbackend.domain.aichat.dto;

// "AI 코스 추천"(홈탭 배너) 응답 원소 하나 - 프론트가 이걸 그대로 코스 빌더 큐에 채워 넣는다.
// reason은 AI가 이 장소를 왜 골랐는지 한 줄로 설명한 것(성향점수 계산이 아니라 OpenAI가 직접 작성).
public record CourseRecommendationDto(
    Long id,
    String name,
    String category,
    String subCategory,
    String address,
    String imageUrl,
    String reason
) {
}
