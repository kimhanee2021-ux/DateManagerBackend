package org.ict.datemanagerbackend.domain.place.dto;

import java.time.LocalDate;
import java.util.List;

// 팀원끼리 place 데이터를 공유하기 위한 전체 백업/복원 단위(2026-08-20). 로컬 Oracle DB를 각자 따로
// 쓰다 보니 KOPIS/카카오/네이버/TourAPI/숙박 CSV로 어렵게 모은 place 데이터가 사람마다 달라지는 문제를
// 풀기 위해 만들었다 - 관리자 API로 한 번 export 받으면, 다른 팀원은 그 파일을 그대로 import API에
// 보내는 것만으로 자기 로컬 DB에 똑같은 place 데이터를 채울 수 있다(SQL Developer로 테이블을 직접
// 내보내고 옮기는 것보다 안전함 - id를 그대로 옮기지 않고 이름+좌표/외부 소스 키로 다시 연결하기
// 때문에, 받는 사람 DB에 이미 있던 자동증가 id와 절대 충돌하지 않는다).
//
// place_categories(46개 세부분류 기준표)와 place_category_id는 일부러 포함하지 않는다 -
// PlaceCategorySeeder/PlaceCategoryLinker가 서버를 켤 때마다 자동으로 채우고 재연결해주기 때문에
// (PlaceCategoryLinker 주석 참고), 옮길 필요도 없고 옮기면 오히려 두 컴퓨터의 카테고리 id가 달라서
// 어긋날 위험만 생긴다.
//
// performance_rankings(그날의 박스오피스 순위)도 뺐다 - 매일 갱신되는 값이라 옮겨봐야 금방 낡고,
// KOPIS 박스오피스 동기화 자체는 가벼워서(공연 목록 API처럼 페이지를 수백 번 돌 필요 없이 상위
// 몇십 건만 받아옴) 받는 사람이 그냥 /api/admin/places/sync/boxoffice를 한 번 호출하면 된다.
public record PlaceDumpDto(
    String name,
    String category,
    String address,
    Double latitude,
    Double longitude,
    String imageUrl,
    String externalSource,
    String externalId,
    // 아래 6개는 KOPIS 공연류 전용 필드 - 다른 소스는 전부 null.
    LocalDate startDate,
    LocalDate endDate,
    String runtimeText,
    String priceInfo,
    String showTimeInfo,
    String bookingUrl,
    Integer isOpenRun,
    String performanceState,
    StyleDto style,
    RealityDto reality,
    List<String> amenities
) {
  // 5축 성향 점수 + 실내/액티비티 여부. Place 하나당 항상 1개씩 있다(PlaceStyle 주석 참고).
  public record StyleDto(
      Integer scoreEnergy, Integer scoreImmersion, Integer scoreVibe,
      Integer scoreAesthetic, Integer scoreDepth, Integer isIndoor, Integer isActivity
  ) {
  }

  // 웨이팅/예약/가격/주차 등 현실 체킹 태그. 아직 데이터가 거의 없는 테이블이라 null인 장소가 대부분.
  public record RealityDto(
      String waitingStatus, Integer waitingTeams, String reservationType,
      String priceText, String parkingInfo
  ) {
  }
}
