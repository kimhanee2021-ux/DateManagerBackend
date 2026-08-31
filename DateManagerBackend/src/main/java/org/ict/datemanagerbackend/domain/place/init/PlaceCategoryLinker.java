package org.ict.datemanagerbackend.domain.place.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// TourAPI/KOPIS/네이버/박물관/문화행사로 이미 동기화된 실제 Place 데이터에 place_category(세부분류)를
// 이름 키워드로 자동 연결하는 1회성 배치. place_category_id가 이미 채워진 장소는 건드리지 않고,
// null인 장소만 대상으로 하기 때문에(PlaceRepository.findByPlaceCategoryIsNull) 재시작마다 다시 돌아도
// 안전하고 비용도 적다. 키워드가 하나도 안 걸리면 null로 남겨두는데, 이건 의도된 폴백이다 - Place 엔티티
// 주석대로 세분류가 안 된 장소는 PlaceStyle의 중립값(50)을 그대로 매칭에 쓰면 되기 때문 (2026-08-14).
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class PlaceCategoryLinker implements ApplicationRunner {

  private final PlaceRepository placeRepository;
  private final PlaceCategoryRepository placeCategoryRepository;

  @Override
  public void run(ApplicationArguments args) {
    List<Place> targets = placeRepository.findByPlaceCategoryIsNull();
    int linked = 0;

    for (Place place : targets) {
      String rawCategory = place.getCategory();
      String parent;
      String matchedSub;

      if (PlaceCategoryKeywords.PERFORMANCE_GENRES.contains(rawCategory)) {
        parent = "공연";
        matchedSub = PlaceCategoryKeywords.resolvePerformanceSubCategory(rawCategory, place.getName());
      } else {
        parent = PlaceCategoryKeywords.PARENT_ALIASES.getOrDefault(rawCategory, rawCategory);
        matchedSub = place.getName() != null ? PlaceCategoryKeywords.findSubCategory(parent, place.getName()) : null;

        // CultureEventSyncServiceImpl이 실제 "전시"(공공데이터 realmName) 데이터를 이 소스로만
        // 저장하기 때문에(2026-08-28) - 키워드가 하나도 안 걸려도 "일반 전시"로 확정해도 안전하다.
        // 반면 같은 category="문화시설"이라도 TourAPI/카카오/네이버는 도서관·문화원·영화관·콘서트홀
        // 등이 뒤섞여 있어서 이 소스에는 이 기본값을 적용하지 않는다(사용자와 상의해 결정).
        if (matchedSub == null && "전시".equals(parent) && "CULTUREINFO".equals(place.getExternalSource())) {
          matchedSub = "일반 전시";
        }
      }

      if (matchedSub == null) {
        continue;
      }

      PlaceCategory category = placeCategoryRepository
          .findByParentCategoryAndSubCategory(parent, matchedSub)
          .orElse(null);
      if (category == null) {
        continue;
      }

      place.setPlaceCategory(category);
      placeRepository.save(place);
      linked++;
    }

    log.info("PlaceCategory 자동 연결 완료 (대상 {}건 중 {}건 연결)", targets.size(), linked);
  }
}
