package org.ict.datemanagerbackend.domain.place.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

// 세부분류별 대표 이미지(2026-08-31) - 실제 장소 사진(Place.imageUrl)이 없는 카드에 대신 보여줄
// 이미지 URL을 PlaceCategory.representativeImageUrl에 채운다. PlaceCategorySeeder(성향점수용)와
// 분리한 이유: 아직 일부 세부분류(숙박 6종)만 이미지를 확보했고, 나머지는 찾는 대로 이 맵에 추가할
// 예정이라 - 전체 세부분류를 다 채워야 하는 PlaceCategorySeeder의 Seed 레코드에 필드를 넣으면
// 관련 없는 수십 개 항목까지 다 건드리게 되어 분리했다. 여기 없는 세부분류는 representativeImageUrl이
// null로 남고, 프론트는 그 경우 기존처럼 이모지/기본 아이콘으로 폴백한다.
// 이미지 출처(전부 상업적 이용 가능):
//  - 한옥: Wikimedia Commons "전주한옥마을 전경.JPG" (CC BY-SA 3.0, 출처표시 필요)
//  - 나머지 5종: Pexels(Pexels License - 출처표시 불필요, 상업적 이용 가능)
// 특정 업체를 사칭하지 않도록 세부분류를 대표하는 일반적인 사진만 골랐고, 프론트는 이 대표이미지가
// 쓰였을 때 "실사진 준비중" 배지를 같이 띄운다(CurationPlaceDto/PlaceResponseDto.imagePlaceholder).
@Slf4j
@Component
@RequiredArgsConstructor
@Order(4) // PlaceCategorySeeder(1)가 만든 행이 먼저 있어야 함 - PlaceCategoryLinksSeeder(3)와는 무관, 겹치지만 않게 4번으로 둠
public class PlaceCategoryImageSeeder implements ApplicationRunner {

  private final PlaceCategoryRepository placeCategoryRepository;

  // 2026-08-31 미리보기 검토 후 확정된 픽 - 모텔은 후보B, 호텔은 후보A, 게스트하우스는 후보A로 교체
  // (기존 픽이 각각 "너무 침침함"/"실내 커튼이라 애매함"/"한옥풍이라 게스트하우스 느낌 아님" 피드백을 받음).
  // 모텔/호텔/게스트하우스는 물량이 많아 카드 여러 개가 동시에 같은 사진으로 보이는 게 단조롭다는
  // 피드백을 받아 PlaceRepresentativeImageResolver에서 후보 3장을 장소 ID로 로테이션하도록 바꿨다 -
  // 이 3개 키의 값은 그 로테이션 목록의 첫 번째 항목과 동일하게만 맞춰두는 참고용이고, 실제 응답에는
  // 안 쓰인다(resolver가 이 3개는 항상 로테이션 목록을 우선한다). 나머지(펜션/리조트/한옥)는 여기 값이
  // 그대로 응답에 쓰인다.
  private static final Map<String, String> LODGING_IMAGES = Map.ofEntries(
      Map.entry("모텔", "https://images.pexels.com/photos/30171418/pexels-photo-30171418.jpeg"),
      Map.entry("호텔", "https://images.pexels.com/photos/36428237/pexels-photo-36428237.jpeg"),
      Map.entry("펜션", "https://images.pexels.com/photos/32405265/pexels-photo-32405265.jpeg"),
      Map.entry("게스트하우스", "https://images.pexels.com/photos/17136827/pexels-photo-17136827.jpeg"),
      Map.entry("리조트", "https://images.pexels.com/photos/9400920/pexels-photo-9400920.jpeg"),
      Map.entry("한옥", "https://upload.wikimedia.org/wikipedia/commons/f/f2/%EC%A0%84%EC%A3%BC%ED%95%9C%EC%98%A5%EB%A7%88%EC%9D%84_%EC%A0%84%EA%B2%BD.JPG")
  );

  @Override
  public void run(ApplicationArguments args) {
    int updated = 0;
    for (Map.Entry<String, String> e : LODGING_IMAGES.entrySet()) {
      var categoryOpt = placeCategoryRepository.findByParentCategoryAndSubCategory("숙박", e.getKey());
      if (categoryOpt.isEmpty()) {
        log.warn("[PlaceCategoryImageSeeder] 숙박 > {} 세부분류를 찾을 수 없어 대표이미지 스킵", e.getKey());
        continue;
      }
      PlaceCategory category = categoryOpt.get();
      category.setRepresentativeImageUrl(e.getValue());
      placeCategoryRepository.save(category);
      updated++;
    }
    log.info("[PlaceCategoryImageSeeder] 세부분류 대표이미지 {}건 반영 완료", updated);
  }
}
