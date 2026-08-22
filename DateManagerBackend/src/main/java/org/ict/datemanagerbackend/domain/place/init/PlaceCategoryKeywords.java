package org.ict.datemanagerbackend.domain.place.init;

import java.util.List;
import java.util.Map;
import java.util.Set;

// PlaceCategoryLinker(이름 키워드 기반 자동 연결)에서 쓰던 세부분류 키워드 테이블을 공용으로
// 쓸 수 있게 분리했다(2026-08-22) - 네이버 지역 검색 API가 내려주는 category 문자열(예:
// "숙박>펜션")을 매칭할 때도 같은 신뢰할 수 있는 키워드 테이블을 재사용하기 위함
// (NaverCategoryMatchService 참고). 데이터/로직 자체는 원래 PlaceCategoryLinker에 있던 것을
// 그대로 옮긴 것이라 동작 변화는 없다.
public final class PlaceCategoryKeywords {

  private PlaceCategoryKeywords() {
  }

  // Place.category(동기화 소스가 저장한 대분류 문자열) -> PlaceCategory.parentCategory 표기가 다른 것들.
  public static final Map<String, String> PARENT_ALIASES = Map.of(
      "박물관/미술관", "박물관·미술관"
  );

  private static Map<String, List<String>> buildOrderedShoppingKeywords() {
    Map<String, List<String>> m = new java.util.LinkedHashMap<>();
    m.put("야시장", List.of("야시장"));
    m.put("전통시장", List.of("시장"));
    m.put("편집숍/빈티지샵", List.of("편집숍", "빈티지"));
    m.put("백화점", List.of("백화점"));
    m.put("아울렛", List.of("아울렛"));
    m.put("플리마켓/팝업", List.of("플리마켓", "팝업스토어"));
    return m;
  }

  // parentCategory -> (subCategory -> 이름/네이버 카테고리에 포함되어 있으면 그 세부분류로 판단할 키워드 목록).
  public static final Map<String, Map<String, List<String>>> KEYWORDS = Map.ofEntries(
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
      Map.entry("쇼핑", buildOrderedShoppingKeywords()),
      Map.entry("축제", Map.of(
          "불꽃/야간축제", List.of("불꽃축제", "불꽃"),
          "음식축제", List.of("음식축제", "푸드페스티벌"),
          "문화예술축제", List.of("영화제", "예술제")
      ))
  );

  // KOPIS 공연 데이터는 Place.category 자체가 장르명(예: "뮤지컬")으로 저장돼 있어서 별도 처리.
  public static final Set<String> PERFORMANCE_GENRES = Set.of(
      "서양음악(클래식)", "뮤지컬", "한국음악(국악)", "무용(서양/한국무용)", "대중무용",
      "연극", "대중음악", "서커스/마술", "복합"
  );

  public static String resolvePerformanceSubCategory(String genre, String placeName) {
    String name = placeName == null ? "" : placeName;
    return switch (genre) {
      case "서양음악(클래식)" -> "클래식/오페라";
      case "뮤지컬" -> "뮤지컬";
      case "한국음악(국악)" -> "국악/전통공연";
      case "무용(서양/한국무용)", "대중무용" -> "무용/발레";
      case "서커스/마술" -> "서커스/마술";
      case "복합" -> "복합공연";
      case "연극" -> name.contains("이머시브") ? "이머시브 연극" : "관람형 연극";
      case "대중음악" -> {
        if (name.contains("페스티벌") || name.contains("페스타")) yield "뮤직페스티벌";
        if (name.contains("어쿠스틱") || name.contains("버스킹") || name.contains("소극장")) {
          yield "소극장/어쿠스틱 콘서트(발라드류)";
        }
        yield "대형 콘서트(아이돌/스타디움)";
      }
      default -> null;
    };
  }

  // parent 카테고리의 키워드 테이블에서 text(장소명 또는 네이버 category 문자열) 안에 포함된
  // 첫 번째 키워드를 찾아 세부분류 이름을 반환한다. 없으면 null(중립으로 남김).
  public static String findSubCategory(String parent, String text) {
    if (text == null) return null;
    Map<String, List<String>> subKeywords = KEYWORDS.get(parent);
    if (subKeywords == null) return null;
    for (Map.Entry<String, List<String>> entry : subKeywords.entrySet()) {
      boolean hit = entry.getValue().stream().anyMatch(text::contains);
      if (hit) return entry.getKey();
    }
    return null;
  }
}
