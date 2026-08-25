package org.ict.datemanagerbackend.domain.place.dto;

// 리뷰 기반 장소별 점수 보정 파일럿(2026-08-25) 1건의 결과. imageVerified가 false면 AI가 준 이미지
// URL이 실제로 안 열리거나 이미지가 아니라서 Place.imageUrl에 반영하지 않았다는 뜻.
public record PlaceReviewResultDto(
    Long placeId,
    String name,
    boolean success,
    String error,
    Integer scoreEnergy,
    Integer scoreImmersion,
    Integer scoreVibe,
    Integer scoreAesthetic,
    Integer scoreDepth,
    Integer scorePacing,
    Double rating,
    Integer reviewCount,
    String imageUrl,
    boolean imageVerified,
    boolean imageApplied,
    String summary
) {
}
