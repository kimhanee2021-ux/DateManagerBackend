package org.ict.datemanagerbackend.domain.place.dto;

import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;

import java.util.List;

// 큐레이션 탭(프론트 CurationTab.jsx)에 내려주는 카드용 DTO. 실제 DB에 있는 필드만 담는다 - 평점/후기수/
// 체크인·체크아웃/최대인원처럼 애초에 저장하는 테이블이 없는 값은 아예 필드로 안 만들었고, 프론트가
// 그런 항목은 "정보 준비중" 같은 문구로 알아서 처리한다(2026-08-14 결정).
// matchScore는 지금은 항상 null - 로그인 유저 성향값을 저장하는 파이프라인이 아직 없어서(Task #20),
// 그동안은 프론트가 최신순으로 대체 정렬한다.
// 5축 점수(scoreEnergy 등)는 원래 큐레이션 카드에 없었는데, 원본 기획서("스마트 데이트 큐레이션 -
// Style Sync Engine")의 핵심이 "장소 성향과 유저 성향을 비교한 매칭 배지"라서 2026-08-19에 추가함 -
// PlaceResponseDto와 동일한 우선순위(place_category 우선, 없으면 place_styles)로 채운다.
public record CurationPlaceDto(
    Long id,
    String name,
    String parentCategory,
    String subCategory,
    String emoji,
    String address,
    String imageUrl,
    Boolean imagePlaceholder,
    String priceText,
    String waitingStatus,
    Integer waitingTeams,
    String parkingInfo,
    List<String> amenities,
    String bookingUrl,
    String runtimeText,
    Integer matchScore,
    Integer boxOfficeRank,
    String genreName,
    Integer scoreEnergy,
    Integer scoreImmersion,
    Integer scoreVibe,
    Integer scoreAesthetic,
    Integer scoreDepth,
    Integer scorePacing
) {

  public static CurationPlaceDto from(
      Place place,
      PlaceCategory category,
      PlaceReality reality,
      List<String> amenities,
      PerformanceRanking ranking,
      PlaceStyle style
  ) {
    // 실제 사진(place.imageUrl)이 없을 때만 세부분류 대표이미지로 대체 - 사칭 방지를 위해 프론트가
    // "실사진 준비중" 배지를 같이 띄울 수 있도록 imagePlaceholder로 대체 여부를 함께 내려준다.
    boolean usePlaceholder = PlaceRepresentativeImageResolver.isPlaceholder(place, category);
    String resolvedImageUrl = PlaceRepresentativeImageResolver.resolve(place, category);
    return new CurationPlaceDto(
        place.getId(),
        place.getName(),
        place.getCategory(),
        category != null ? category.getSubCategory() : null,
        category != null ? category.getEmoji() : null,
        place.getAddress(),
        resolvedImageUrl,
        usePlaceholder,
        reality != null ? reality.getPriceText() : null,
        reality != null ? reality.getWaitingStatus() : null,
        reality != null ? reality.getWaitingTeams() : null,
        reality != null ? reality.getParkingInfo() : null,
        amenities,
        place.getBookingUrl(),
        place.getRuntimeText(),
        null,
        ranking != null ? ranking.getRankNo() : null,
        ranking != null ? ranking.getGenreName() : null,
        category != null ? category.getScoreEnergy() : (style != null ? style.getScoreEnergy() : null),
        category != null ? category.getScoreImmersion() : (style != null ? style.getScoreImmersion() : null),
        category != null ? category.getScoreVibe() : (style != null ? style.getScoreVibe() : null),
        category != null ? category.getScoreAesthetic() : (style != null ? style.getScoreAesthetic() : null),
        category != null ? category.getScoreDepth() : (style != null ? style.getScoreDepth() : null),
        category != null ? category.getScorePacing() : (style != null ? style.getScorePacing() : null)
    );
  }
}
