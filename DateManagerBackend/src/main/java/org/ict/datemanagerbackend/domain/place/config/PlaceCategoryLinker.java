package org.ict.datemanagerbackend.domain.place.config;

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
import java.util.Map;

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

  // Place.category(동기화 소스가 저장한 대분류 문자열) -> PlaceCategory.parentCategory 표기가 다른 것들.
  // MuseumSyncService는 "박물관/미술관"(슬래시)으로 저장하는데 PlaceCategory 쪽은 "박물관·미술관"(가운뎃점).
  private static final Map<String, String> PARENT_ALIASES = Map.of(
      "박물관/미술관", "박물관·미술관"
  );

  // parentCategory -> (subCategory -> 이름에 포함되어 있으면 그 세부분류로 판단할 키워드 목록).
  // 세부분류 이름 자체가 이미 좋은 키워드인 경우가 많아서 최대한 재사용했고, "산"/"거리"처럼 너무 짧고
  // 흔한 단어는 오탐이 심해서 일부러 뺐다 - 그런 경우는 그냥 매칭 안 되고 null(중립)로 남는다.
  private static final Map<String, Map<String, List<String>>> KEYWORDS = Map.ofEntries(
      Map.entry("맛집", Map.of(
          "조용한 다이닝(파인다이닝/오마카세)", List.of("파인다이닝", "오마카세"),
          "캐주얼 맛집/브런치", List.of("브런치"),
          "술집/포차/이자카야", List.of("이자카야", "포차", "호프", "와인바"),
          "로컬 노포/전통시장", List.of("노포")
      )),
      Map.entry("공연", Map.of(
          "클래식/오페라", List.of("클래식", "오페라"),
          "뮤지컬", List.of("뮤지컬"),
          "소극장/어쿠스틱 콘서트(발라드류)", List.of("소극장", "어쿠스틱"),
          "대형 콘서트(아이돌/스타디움)", List.of("콘서트", "아레나"),
          "이머시브 연극", List.of("이머시브"),
          "관람형 연극", List.of("연극"),
          "무용/발레", List.of("무용", "발레"),
          "국악/전통공연", List.of("국악", "판소리"),
          "뮤직페스티벌", List.of("뮤직페스티벌")
      )),
      // "여관"/"여인숙"은 키워드에 없어서 전부 null로 빠지고 있었다(2026-08-14, 숙박 탭 확인 중 발견) -
      // 성격상 모텔에 가장 가까워서 모텔 키워드에 추가. "민박"은 개인 집을 빌리는 형태라 펜션에 추가.
      Map.entry("숙박", Map.of(
          "호텔", List.of("호텔"),
          "리조트", List.of("리조트"),
          "펜션", List.of("펜션", "민박"),
          "한옥", List.of("한옥"),
          "게스트하우스", List.of("게스트하우스"),
          "모텔", List.of("모텔", "여관", "여인숙")
      )),
      Map.entry("전시", Map.of(
          "도슨트 전시", List.of("도슨트")
      )),
      Map.entry("박물관·미술관", Map.of(
          "상설미술관", List.of("미술관"),
          "역사/과학박물관", List.of("역사박물관", "과학관", "박물관"),
          "사진/미디어아트관", List.of("미디어아트", "포토뮤지엄")
      )),
      Map.entry("관광지", Map.of(
          "전망/야경", List.of("전망대", "스카이"),
          "역사/유적", List.of("고궁", "사찰", "유적"),
          "골목/동네산책", List.of("거리", "골목"),
          "테마파크", List.of("테마파크", "놀이공원"),
          "랜드마크", List.of("타워", "랜드마크")
      )),
      Map.entry("액티비티", Map.of(
          "방탈출/미스터리룸", List.of("방탈출", "미스터리룸"),
          "VR/실감형체험", List.of("VR"),
          "클라이밍/스포츠체험", List.of("클라이밍"),
          "볼링/당구/오락실", List.of("볼링", "당구", "오락실", "스크린골프"),
          "수상레포츠", List.of("서핑", "패들보드", "카약"),
          "캠핑/글램핑", List.of("캠핑", "글램핑"),
          "원데이클래스", List.of("원데이클래스", "쿠킹클래스")
      )),
      Map.entry("쇼핑", Map.of(
          "전통시장", List.of("전통시장"),
          "야시장", List.of("야시장"),
          "편집숍/빈티지샵", List.of("편집숍", "빈티지"),
          "백화점", List.of("백화점"),
          "아울렛", List.of("아울렛"),
          "플리마켓/팝업", List.of("플리마켓", "팝업스토어")
      )),
      Map.entry("축제", Map.of(
          "불꽃/야간축제", List.of("불꽃축제", "불꽃"),
          "음식축제", List.of("음식축제", "푸드페스티벌"),
          "문화예술축제", List.of("영화제", "예술제")
      ))
  );

  @Override
  public void run(ApplicationArguments args) {
    List<Place> targets = placeRepository.findByPlaceCategoryIsNull();
    int linked = 0;

    for (Place place : targets) {
      String parent = PARENT_ALIASES.getOrDefault(place.getCategory(), place.getCategory());
      Map<String, List<String>> subKeywords = KEYWORDS.get(parent);
      if (subKeywords == null || place.getName() == null) {
        continue;
      }

      String matchedSub = null;
      for (Map.Entry<String, List<String>> entry : subKeywords.entrySet()) {
        boolean hit = entry.getValue().stream().anyMatch(place.getName()::contains);
        if (hit) {
          matchedSub = entry.getKey();
          break;
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
