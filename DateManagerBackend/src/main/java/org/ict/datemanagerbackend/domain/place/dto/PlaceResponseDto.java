package org.ict.datemanagerbackend.domain.place.dto;

import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;

import java.time.LocalDate;
import java.util.List;

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
    Boolean imagePlaceholder,
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
    String emoji,
    String venueName,
    String venueId,
    String venueImageUrl,
    String useTime,
    String restDate,
    String parkingInfo,
    List<String> amenityTags,
    String menuInfo,
    String phone,
    String packingInfo
) {

  public static PlaceResponseDto from(Place place, PlaceStyle style) {
    return from(place, style, null, null, List.of());
  }

  // 홈탭 "즉흥·계획" 자리에 공연 예매율 순위를 보여주려면 nearby 응답에도 boxOfficeRank/장르가
  // 필요해서 큐레이션 DTO처럼 PerformanceRanking을 선택적으로 받는 오버로드를 추가함(2026-08-19).
  public static PlaceResponseDto from(Place place, PlaceStyle style, PerformanceRanking ranking) {
    return from(place, style, ranking, null, List.of());
  }

  // 장소 상세 화면(GET /api/places/{id})에 영업시간/휴무일/주차/편의태그를 실어주기 위한
  // 오버로드(2026-08-31) - PlaceReality/PlaceAmenity는 TourAPI 상세조회로만 채워지는 선택적
  // 데이터라 둘 다 null/빈 리스트를 기본값으로 허용한다.
  public static PlaceResponseDto from(Place place, PlaceStyle style, PerformanceRanking ranking,
                                       PlaceReality reality, List<String> amenityTags) {
    PlaceCategory category = place.getPlaceCategory();
    // 실제 사진(place.imageUrl)이 없을 때만 세부분류 대표이미지로 대체 - 사칭 방지를 위해 프론트가
    // "실사진 준비중" 배지를 같이 띄울 수 있도록 imagePlaceholder로 대체 여부를 함께 내려준다.
    boolean usePlaceholder = PlaceRepresentativeImageResolver.isPlaceholder(place, category);
    String resolvedImageUrl = PlaceRepresentativeImageResolver.resolve(place, category);
    return new PlaceResponseDto(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        resolvedImageUrl,
        usePlaceholder,
        category != null ? category.getScoreEnergy() : (style != null ? style.getScoreEnergy() : null),
        category != null ? category.getScoreImmersion() : (style != null ? style.getScoreImmersion() : null),
        category != null ? category.getScoreVibe() : (style != null ? style.getScoreVibe() : null),
        category != null ? category.getScoreAesthetic() : (style != null ? style.getScoreAesthetic() : null),
        category != null ? category.getScoreDepth() : (style != null ? style.getScoreDepth() : null),
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
        category != null ? category.getEmoji() : null,
        place.getVenueName(),
        place.getVenueId(),
        place.getVenueImageUrl(),
        reality != null ? reality.getUseTime() : null,
        reality != null ? reality.getRestDate() : null,
        reality != null ? reality.getParkingInfo() : null,
        amenityTags != null ? amenityTags : List.of(),
        reality != null ? reality.getMenuInfo() : null,
        reality != null ? reality.getPhone() : null,
        reality != null ? reality.getPackingInfo() : null
    );
  }
}
