package org.ict.datemanagerbackend.domain.place.dto;

import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;

import java.time.LocalDate;

// 큐레이션/코스빌더 화면에 내려주는 장소 응답 DTO
// style은 아직 이 place의 place_styles 행이 없을 수도 있어서(예: 새로 동기화된 장소) null 허용.
// startDate ~ performanceState는 공연류(KOPIS) 장소만 값이 있고, 나머지 소스는 전부 null.
// 5축 점수는 place_category(세부분류 공식)가 연결돼 있으면 그걸 우선 쓰고, 없으면 place_styles로
// 폴백한다 - PlaceMatchService.resolvePlaceScores()와 동일한 우선순위(2026-08-19). place_styles는
// place_categories 도입 전에 채워진 값이 많아 지금은 세부분류와 안 맞는 값(예: 조용한 다이닝인데
// score_vibe=60처럼 다른 맛집과 똑같은 값)이 섞여 있어서, 실제로 성향 매칭을 하려면 이 우선순위가
// 꼭 필요하다.
public record PlaceResponseDto(
    Long id,
    String name,
    String category,
    String address,
    Double latitude,
    Double longitude,
    String imageUrl,
    Integer scoreEnergy,
    Integer scoreImmersion,
    Integer scoreVibe,
    Integer scoreAesthetic,
    Integer scoreDepth,
    Integer isIndoor,
    Integer isActivity,
    LocalDate startDate,
    LocalDate endDate,
    String runtimeText,
    String priceInfo,
    String showTimeInfo,
    String bookingUrl,
    Integer isOpenRun,
    String performanceState,
    Integer boxOfficeRank,
    String genreName,
    String emoji
) {

  public static PlaceResponseDto from(Place place, PlaceStyle style) {
    return from(place, style, null);
  }

  // 홈탭 "즉흥·계획" 자리에 공연 예매율 순위를 보여주려면 nearby 응답에도 boxOfficeRank/장르가
  // 필요해서 큐레이션 DTO처럼 PerformanceRanking을 선택적으로 받는 오버로드를 추가함(2026-08-19).
  public static PlaceResponseDto from(Place place, PlaceStyle style, PerformanceRanking ranking) {
    PlaceCategory category = place.getPlaceCategory();
    // 리뷰 기반 개별 보정이 끝난 장소(reviewed=true)는 카테고리 공통값보다 그 장소 고유값을
    // 우선한다 - PlaceMatchServiceImpl.resolvePlaceScores()와 동일한 우선순위(2026-08-25).
    boolean useStyle = style != null && Boolean.TRUE.equals(style.getReviewed());
    return new PlaceResponseDto(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getImageUrl(),
        useStyle ? style.getScoreEnergy() : (category != null ? category.getScoreEnergy() : (style != null ? style.getScoreEnergy() : null)),
        useStyle ? style.getScoreImmersion() : (category != null ? category.getScoreImmersion() : (style != null ? style.getScoreImmersion() : null)),
        useStyle ? style.getScoreVibe() : (category != null ? category.getScoreVibe() : (style != null ? style.getScoreVibe() : null)),
        useStyle ? style.getScoreAesthetic() : (category != null ? category.getScoreAesthetic() : (style != null ? style.getScoreAesthetic() : null)),
        useStyle ? style.getScoreDepth() : (category != null ? category.getScoreDepth() : (style != null ? style.getScoreDepth() : null)),
        style != null ? style.getIsIndoor() : null,
        style != null ? style.getIsActivity() : null,
        place.getStartDate(),
        place.getEndDate(),
        place.getRuntimeText(),
        place.getPriceInfo(),
        place.getShowTimeInfo(),
        place.getBookingUrl(),
        place.getIsOpenRun(),
        place.getPerformanceState(),
        ranking != null ? ranking.getRankNo() : null,
        ranking != null ? ranking.getGenreName() : null,
        // 이미지 없는 카드의 대체 아이콘용(2026-08-22, HomeTab 카드 개선) - 세부분류 이모지가
        // 있으면 그걸, 없으면 null(프론트가 기본 📍로 폴백).
        category != null ? category.getEmoji() : null
    );
  }
}
