package org.ict.datemanagerbackend.domain.course.dto;

// 자동 매칭(성향 보완 스팟) 추천 결과 한 건. 기획서("데이트 코스 빌더 - Course Sync Builder")의
// "Step 2: 7대 성향 점수 분석 후, 이동 시간 20분 이내의 성향 보완 스팟 자동 추천"을 구현한 결과.
// 실제로 코스에 담으려면 이 중 placeId를 골라 /items로 다시 보내야 한다(추천은 담기가 아님).
public record CourseMatchSuggestionDto(
    Long placeId,
    String name,
    String category,
    String subCategory,
    String address,
    Double latitude,
    Double longitude,
    Integer scoreEnergy,
    Double distanceMeters,
    Integer estimatedWalkMinutes
) {
}
