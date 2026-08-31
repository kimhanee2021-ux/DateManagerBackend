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
  // "문화시설"->"전시"(2026-08-28 추가): CultureEventSyncServiceImpl이 "전시"(공공데이터 realmName)
  // 데이터를 Place.category="문화시설"로 저장하는데, 이 별칭이 없어서 PlaceCategoryLinker가
  // parent="문화시설"로 KEYWORDS를 찾다가 그런 키가 아예 없어 즉시 null(미분류)로 끝나버렸다 -
  // 그래서 "전시" 세부칩("도슨트 전시" 등)이 이미 있고 키워드도 등록돼 있었는데도 실제로는 5,172건
  // 전부 단 한 번도 매칭에 안 쓰이고 있었다(사용자가 큐레이션 탭에서 "전시" 세부칩 개수가 전부
  // 0이라고 지적해서 발견).
  public static final Map<String, String> PARENT_ALIASES = Map.of(
      "박물관/미술관", "박물관·미술관",
      "문화시설", "전시"
  );

  // 소상공인시장진흥공단 상가정보 API가 내려주는 표준 업종명(예: "한식음식점", "커피전문점")을
  // 매칭하기 위해 추가(2026-08-27) - place_categories에는 이미 한식/중식/일식/양식/아시아음식/
  // 분식/뷔페/카페(일반)/카페(브런치) 세부분류(id 101~110)가 있었지만 키워드 테이블이 비어있어서
  // 실제로는 한 번도 매칭에 쓰이지 않고 있었다. 순서가 중요 - "이자카야"는 "술집/포차/이자카야"가
  // 먼저 걸리게 위쪽에 남겨두고, 신규 업종 키워드는 그 아래에 추가한다.
  private static Map<String, List<String>> buildOrderedRestaurantKeywords() {
    Map<String, List<String>> m = new java.util.LinkedHashMap<>();
    m.put("조용한 다이닝(파인다이닝/오마카세)", List.of("파인다이닝", "오마카세"));
    m.put("술집/포차/이자카야", List.of("이자카야", "포차", "호프", "와인바"));
    m.put("로컬 노포/전통시장", List.of("노포"));
    m.put("캐주얼 맛집/브런치", List.of("브런치"));
    m.put("카페(브런치)", List.of("브런치카페"));
    m.put("카페(일반)", List.of("카페", "커피전문점", "커피", "로스터리", "베이커리카페"));
    m.put("식당(한식)", List.of("한식", "한정식", "백반"));
    m.put("식당(중식)", List.of("중식", "중국집", "짜장면", "짬뽕"));
    m.put("식당(일식)", List.of("일식", "초밥", "스시", "라멘", "돈카츠"));
    m.put("식당(양식)", List.of("양식", "파스타", "스테이크", "피자"));
    m.put("식당(아시아음식)", List.of("베트남음식", "태국음식", "쌀국수", "팟타이", "인도음식", "커리"));
    m.put("식당(분식)", List.of("분식", "떡볶이", "순대"));
    m.put("식당(뷔페)", List.of("뷔페"));
    return m;
  }

  // "문화시설"(TourAPI/카카오/네이버)로 들어오는 이름은 실제로 미술관/갤러리/전시관뿐 아니라 영화관
  // (메가박스/CGV/롯데시네마), 도서관, 문화원, 콘서트홀/아트홀, 모임공간까지 뒤섞여 있다(2026-08-28
  // 실측 - 150건 샘플로 확인). 그중 확실히 "전시를 보러 가는 곳"이라고 판단되는 키워드만 추가하고,
  // 극장/센터/홀처럼 너무 넓어서 오분류 위험이 큰 키워드는 일부러 뺐다 - "도슨트"가 먼저 걸리게
  // 순서를 유지한다(도슨트 전시관처럼 둘 다 포함된 이름은 도슨트 전시로 우선 분류).
  private static Map<String, List<String>> buildOrderedExhibitionKeywords() {
    Map<String, List<String>> m = new java.util.LinkedHashMap<>();
    // "도슨트"라는 단어 자체가 시설명에 들어가는 경우는 실측 0건이라(2026-08-28) 폐기하고, 대신
    // "보기 좋음"보다 "이야기·해설"이 중심인 콘텐츠를 모으는 용도로 재정의했다(사용자 지정 키워드) -
    // 미술관/갤러리(시각 중심)와 구분해서 화랑(전통 갤러리 표현)·역사관·홍보관·자료관·문화관·과학관·
    // 문학관·예술의전당·천문대는 전부 여기로.
    m.put("역사·이야기 전시", List.of("화랑", "역사관", "홍보관", "자료관", "문화관", "과학관", "문학관", "예술의전당", "천문대"));
    m.put("영화관", List.of("영화관", "시네마", "메가박스", "CGV", "롯데시네마"));
    m.put("도서관", List.of("도서관"));
    m.put("문화원", List.of("문화원"));
    // 그라운드시소/오디움류(요즘 유행하는 몰입형 미디어아트 전시 브랜드, 2026-08-28 사용자 확인) -
    // "미디어아트"/"뮤지엄" 같은 일반 키워드에 안 걸리는 고유 브랜드명이라 따로 추가.
    // "일루전"/"일루젼"은 마술쇼 이름으로도 흔히 쓰여서(예: KOPIS "팡팡 벌룬 일루전" 공연) 빼뒀다 -
    // 실제로 문화시설 미분류 데이터 중엔 매칭된 게 없었지만, 나중에 마술쇼가 이 키워드로 잘못
    // 분류되는 걸 막기 위해 일부러 포함하지 않는다(2026-08-28, 사용자 지적).
    // 사진은 이미 있는데(TourAPI 대표사진 확보됨) 세부분류만 안 붙어있던 693건 재검토(2026-08-28)에서
    // 추가로 찾은 키워드 - 시각적 볼거리 중심인 것만 남기고, 이야기·해설 중심(화랑/역사관/홍보관/
    // 자료관/문화관/과학관/문학관/예술의전당/천문대)은 위 "역사·이야기 전시"로 뺐다(사용자 지정).
    m.put("일반 전시", List.of("전시관", "전시실", "갤러리", "미술관", "뮤지엄", "박물관", "기념관",
        "그라운드시소", "오디움", "빛의벙커", "아르떼", "디스트릭트", "아트스페이스", "아트플랫폼", "문화플랫폼",
        "컨벤션센터", "벡스코", "코엑스", "아쿠아리움", "아쿠아플라넷"));
    return m;
  }

  private static Map<String, List<String>> buildOrderedShoppingKeywords() {
    Map<String, List<String>> m = new java.util.LinkedHashMap<>();
    m.put("야시장", List.of("야시장"));
    m.put("전통시장", List.of("시장"));
    m.put("편집숍/빈티지샵", List.of("편집숍", "빈티지"));
    m.put("백화점", List.of("백화점"));
    m.put("아울렛", List.of("아울렛"));
    m.put("플리마켓/팝업", List.of("플리마켓", "팝업스토어"));
    // 브랜드 매장(2026-08-28) - "플래그십"/"쇼룸"이 직접 들어간 이름 외에도, 성수·한남·청담·
    // 가로수길·도산 같은 플래그십스토어 상권 이름만으로도 실측 결과 노이즈 없이 브랜드 매장으로
    // 확인됐다(사용자가 직접 123건 샘플 확인). 골프용품점처럼 이 동네에 있어도 성격이 다른 소수는
    // 이 키워드로도 걸리지만(예: "OO골프 청담점"), 완전히 배제하긴 어려워 감수한다.
    m.put("브랜드 매장", List.of("플래그십", "플래그쉽", "쇼룸", "성수", "한남", "청담", "가로수길", "도산"));
    return m;
  }

  // parentCategory -> (subCategory -> 이름/네이버 카테고리에 포함되어 있으면 그 세부분류로 판단할 키워드 목록).
  public static final Map<String, Map<String, List<String>>> KEYWORDS = Map.ofEntries(
      Map.entry("맛집", buildOrderedRestaurantKeywords()),
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
      Map.entry("전시", buildOrderedExhibitionKeywords()),
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
      Map.entry("액티비티", Map.ofEntries(
          Map.entry("방탈출/미스터리룸", List.of("방탈출", "미스터리룸")),
          Map.entry("VR/실감형체험", List.of("VR")),
          Map.entry("클라이밍/스포츠체험", List.of("클라이밍", "암벽", "활공", "짚와이어")),
          // "스크린골프"는 새로 생긴 "골프" 세부분류(2026-08-28)의 "골프" 키워드에도 걸리는 문자열이라
          // (Map은 순서 보장이 없어 어느 쪽으로 갈지 불확실해짐) 여기서 빼고 골프 쪽 하나로만 잡는다.
          // 보드게임카페(2026-08-28) - NAVER 지역검색 카테고리 태그가 "오락실"류로 뭉뚱그려져
          // 내려와서 볼링/당구/오락실과 섞이는 문제가 있었다. 이름 기반 매칭이 먼저 걸리도록
          // 볼링/당구/오락실보다 앞에 둔다(둘 다 안 걸리는 이름이라 순서 자체는 무해하지만 의도를
          // 명확히 하기 위해 위에 배치).
          Map.entry("보드게임카페", List.of("보드게임")),
          Map.entry("볼링/당구/오락실", List.of("볼링", "당구", "오락실")),
          Map.entry("수상레포츠", List.of("서핑", "패들보드", "카약")),
          Map.entry("캠핑/글램핑", List.of("캠핑", "글램핑", "글램", "자연휴양림")),
          Map.entry("원데이클래스", List.of("원데이클래스", "쿠킹클래스")),
          // 2026-08-28 사용자 지정 축 점수로 5종 추가 (골프/트레킹/낚시/스키·보드/승마)
          Map.entry("골프", List.of("골프")),
          Map.entry("트레킹/산책길", List.of("둘레길", "옛길", "올레", "숲길", "트레킹", "자드락길", "등산",
              "해파랑길", "평화누리길", "바우길", "누리길")),
          Map.entry("낚시", List.of("낚시")),
          Map.entry("스키/보드", List.of("스키", "스노보드", "썰매장")),
          Map.entry("승마", List.of("승마"))
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
