package org.ict.datemanagerbackend.domain.place.dto;

import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;

import java.util.List;

// 큐레이션 탭(프론트 CurationTab.jsx)에 내려주는 카드용 DTO. 실제 DB에 있는 필드만 담는다 - 평점/후기수/
// 체크인·체크아웃/최대인원처럼 애초에 저장하는 테이블이 없는 값은 아예 필드로 안 만들었고, 프론트가
// 그런 항목은 "정보 준비중" 같은 문구로 알아서 처리한다(2026-08-14 결정).
// matchScore는 지금은 항상 null - 로그인 유저 성향값을 저장하는 파이프라인이 아직 없어서(Task #20),
// 그동안은 프론트가 최신순으로 대체 정렬한다.
public record CurationPlaceDto(
    Long id,
    String name,
    String parentCategory,
    String subCategory,
    String emoji,
    String address,
    String imageUrl,
    String priceText,
    String waitingStatus,
    Integer waitingTeams,
    String parkingInfo,
    List<String> amenities,
    String bookingUrl,
    String runtimeText,
    Integer matchScore
) {

  public static CurationPlaceDto from(
      Place place,
      PlaceCategory category,
      PlaceReality reality,
      List<String> amenities
  ) {
    return new CurationPlaceDto(
        place.getId(),
        place.getName(),
        place.getCategory(),
        category != null ? category.getSubCategory() : null,
        category != null ? category.getEmoji() : null,
        place.getAddress(),
        place.getImageUrl(),
        reality != null ? reality.getPriceText() : null,
        reality != null ? reality.getWaitingStatus() : null,
        reality != null ? reality.getWaitingTeams() : null,
        reality != null ? reality.getParkingInfo() : null,
        amenities,
        place.getBookingUrl(),
        place.getRuntimeText(),
        null
    );
  }
}
