package org.ict.datemanagerbackend.domain.place.service;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.KakaoLocalPlaceDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 카카오 로컬 API(키워드로 장소 검색, https://dapi.kakao.com/v2/local/search/keyword.json)에서
 * 장소 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * <p>NaverPlaceSyncService와 구조는 같지만(지역명 × 카테고리 조합으로 여러 번 검색), 카테고리 결정
 * 방식이 다르다 - 네이버는 검색 키워드로 카테고리를 짐작해야 하는데, 카카오는 검색 결과에
 * {@code category_group_code}(음식점/카페/숙박/관광명소/문화시설 등 표준 분류)가 같이 내려와서
 * 그걸로 바로 우리 대분류에 매핑한다. CT1(문화시설)은 마침 우리 "전시" 칩이 가리키는 원본 category
 * 값("문화시설")과 이름이 같아서 별도 별칭 매핑 없이 그대로 맞아떨어진다.
 *
 * <p>한 페이지에 최대 15건, 최대 45페이지까지 지원해서(네이버는 검색어당 최대 5건 고정) 지역당 페이지를
 * 2장까지만 받아도 네이버보다 훨씬 많은 데이터를 모을 수 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoPlaceSyncService {

  private static final String SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
  private static final String EXTERNAL_SOURCE = "KAKAO";
  private static final int PAGE_SIZE = 15;
  private static final int MAX_PAGES_PER_QUERY = 2; // 지역당 최대 30건까지만(과도한 API 호출 방지)

  // 카카오 표준 카테고리 그룹 코드 -> 우리 대분류 매핑. CT1은 일부러 "문화시설"로 둔다 - 우리
  // "전시" 칩이 실제로 가리키는 Place.category 원본 값이 "문화시설"이라(문화행사 동기화와 같은 이유,
  // 2026-08-14) 여기서도 똑같이 맞춰야 큐레이션 탭 "전시" 칩에 자동으로 잡힌다.
  private static final Map<String, String> CATEGORY_GROUP_MAP = new LinkedHashMap<>(Map.of(
      "FD6", "맛집",
      "CE7", "맛집",
      "AD5", "숙박",
      "AT4", "관광지",
      "CT1", "문화시설"
  ));

  // 카카오 category_name 2단계(음식점 > OO > ...)를 우리 세부분류로 매핑 (2026-08-18, 실측으로
  // 확인한 상위 분포 기준 - 한식/일식/중식/양식이 대부분이고 나머지는 소수라 "식당(기타)"로 묶음)
  private static final Map<String, String> CUISINE_SUBCATEGORY_MAP = Map.ofEntries(
      Map.entry("한식", "식당(한식)"),
      Map.entry("중식", "식당(중식)"),
      Map.entry("일식", "식당(일식)"),
      Map.entry("양식", "식당(양식)"),
      Map.entry("아시아음식", "식당(아시아음식)"),
      Map.entry("분식", "식당(분식)"),
      Map.entry("뷔페", "식당(뷔페)")
  );

  // 유명 프랜차이즈 제외 목록(2026-08-18) - 지점이 너무 많아 추천 결과가 특정 체인으로 도배되는 걸
  // 막기 위함. 이름에 이 키워드가 포함되면(지점명 앞뒤로 뭐가 붙어도) 동기화 대상에서 제외한다.
  private static final Set<String> FRANCHISE_BLACKLIST = Set.of(
      // 커피 프랜차이즈
      "스타벅스", "이디야", "투썸플레이스", "메가엠지씨커피", "메가커피", "컴포즈커피", "빽다방",
      "커피빈", "할리스", "폴바셋", "탐앤탐스", "파스쿠찌", "엔젤리너스", "매머드커피", "더벤티", "블루보틀",
      // 베이커리/디저트 프랜차이즈
      "파리바게뜨", "뚜레쥬르", "배스킨라빈스", "던킨",
      // 패스트푸드/치킨 프랜차이즈
      "맥도날드", "버거킹", "롯데리아", "서브웨이", "맘스터치",
      "굽네치킨", "교촌치킨", "네네치킨", "푸라닭",
      "도미노피자", "피자헛", "미스터피자", "파파존스"
  );

  // 서울 25개구 + 경기 주요 10개시 + 인천 5개구 + 부산 16개구. NaverPlaceSyncService와 동일한 지역
  // 커버리지(수도권+부산 우선)를 쓴다 - "구/시 이름만 검색하면 인천 서구/부산 서구처럼 겹치는 지명이
  // 섞일 수 있어 시/도 이름을 붙인다"는 이유도 동일.
  private static final List<String> REGIONS = new ArrayList<>();

  static {
    for (String gu : List.of(
        "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
        "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
        "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"
    )) {
      REGIONS.add("서울 " + gu);
    }
    for (String si : List.of(
        "수원시", "성남시", "고양시", "용인시", "부천시",
        "안산시", "안양시", "남양주시", "화성시", "평택시"
    )) {
      REGIONS.add("경기 " + si);
    }
    for (String gu : List.of("남동구", "부평구", "서구", "연수구", "미추홀구")) {
      REGIONS.add("인천 " + gu);
    }
    for (String gu : List.of(
        "해운대구", "부산진구", "동래구", "수영구", "사상구", "사하구", "남구", "서구",
        "중구", "연제구", "금정구", "강서구", "동구", "북구", "영도구", "기장군"
    )) {
      REGIONS.add("부산 " + gu);
    }
  }

  private final PlaceRepository placeRepository;
  private final PlaceDedupService placeDedupService;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${kakao.rest-api-key}")
  private String restApiKey;

  /** 매주 일요일 새벽 5시 30분(TourAPI 4시, 박물관 4시30분, 네이버 5시와 안 겹치게 배치). */
  @Scheduled(cron = "0 30 5 * * SUN")
  public void syncPlaces() {
    // 검색어 기반이라 매 실행마다 결과가 달라질 수 있어 upsert 대신 전체 교체 방식을 쓴다
    // (NaverPlaceSyncService와 동일한 이유 - 폐업/이전된 곳이 계속 남아있는 것도 방지됨).
    placeRepository.deleteByExternalSource(EXTERNAL_SOURCE);

    int created = 0;
    int skippedDuplicate = 0;
    int skippedFranchise = 0;
    int skippedFakeCafe = 0;
    int failedQueries = 0;
    Set<String> seenThisRun = new HashSet<>();

    for (Map.Entry<String, String> entry : CATEGORY_GROUP_MAP.entrySet()) {
      String categoryGroupCode = entry.getKey();
      String category = entry.getValue();

      for (String region : REGIONS) {
        List<KakaoLocalPlaceDto> items;
        try {
          items = searchAllPages(region, categoryGroupCode);
        } catch (Exception e) {
          log.error("카카오 지역 검색 실패 (region={}, categoryGroupCode={})", region, categoryGroupCode, e);
          failedQueries++;
          continue;
        }

        for (KakaoLocalPlaceDto item : items) {
          String address = !item.roadAddressName().isBlank() ? item.roadAddressName() : item.addressName();
          if (item.placeName().isBlank() || address.isBlank()) continue;

          if (isFranchise(item.placeName())) {
            skippedFranchise++;
            continue;
          }

          // CE7은 커피/디저트 카페뿐 아니라 만화카페/보드카페/키즈카페까지 섞여 내려오는데(실측 확인,
          // category_name이 "가정,생활 > 여가시설 > ..."로 완전히 다른 계층), 후자는 음식점이 아니라
          // 놀거리/체험 시설이라 맛집 카테고리에 안 맞아서 뺀다.
          if ("CE7".equals(categoryGroupCode) && !isRealCafe(item.categoryName())) {
            skippedFakeCafe++;
            continue;
          }

          Double lng = parseCoordinate(item.x());
          Double lat = parseCoordinate(item.y());
          String externalId = hashKey(item.placeName() + "|" + address);

          if (!seenThisRun.add(externalId)) {
            continue; // 이번 실행에서 이미 다른 지역/카테고리로 저장한 장소(경계 지역 등에서 중복 조회될 수 있음)
          }

          Optional<Place> duplicate = placeDedupService.findDuplicate(item.placeName(), lat, lng);
          if (duplicate.isPresent()) {
            skippedDuplicate++;
            continue;
          }

          PlaceCategory subCategory = resolveSubCategory(categoryGroupCode, item);

          Place place = Place.builder()
              .name(item.placeName())
              .category(category)
              .placeCategory(subCategory)
              .address(address)
              .latitude(lat)
              .longitude(lng)
              .externalSource(EXTERNAL_SOURCE)
              .externalId(externalId)
              .build();
          placeRepository.save(place);
          created++;
        }

        sleepBriefly();
      }
    }

    log.info("카카오 지역 검색 동기화 완료 - 신규 {}건, 다른 소스와 중복이라 건너뜀 {}건, "
            + "프랜차이즈라 제외 {}건, 카페 아닌 여가시설이라 제외 {}건, 실패한 검색어 {}건",
        created, skippedDuplicate, skippedFranchise, skippedFakeCafe, failedQueries);
  }

  private boolean isFranchise(String placeName) {
    return FRANCHISE_BLACKLIST.stream().anyMatch(placeName::contains);
  }

  // CE7 중 진짜 카페(커피/디저트)인지 - category_name이 "음식점 > 카페"로 시작해야 함
  private boolean isRealCafe(String categoryName) {
    return categoryName != null && categoryName.startsWith("음식점 > 카페");
  }

  /**
   * FD6(음식점)는 category_name 2단계(예: "음식점 > 한식 > 국밥"에서 "한식")로 음식 종류를 판단해서
   * "식당(한식)" 등으로 매핑한다. CE7(카페)는 카카오 분류에 브런치 태그가 따로 없어서 이름에
   * "브런치"가 들어가면 "카페(브런치)", 아니면 "카페(일반)"으로 나눈다(실측 확인, 2026-08-18).
   */
  // 맛집(FD6/CE7) 소스만 세분화 대상이다 - 숙박(AD5)/관광지(AT4)/문화시설(CT1)까지 여기로 오면
  // "맛집" 세부분류가 엉뚱하게 붙어버리므로 그 외 카테고리는 아예 조회하지 않고 null(미분류)로 둔다.
  private PlaceCategory resolveSubCategory(String categoryGroupCode, KakaoLocalPlaceDto item) {
    String subCategoryName;
    if ("CE7".equals(categoryGroupCode)) {
      subCategoryName = item.placeName().contains("브런치") ? "카페(브런치)" : "카페(일반)";
    } else if ("FD6".equals(categoryGroupCode)) {
      String[] parts = item.categoryName() == null ? new String[0] : item.categoryName().split(" > ");
      String cuisine = parts.length >= 2 ? parts[1] : null;
      subCategoryName = cuisine == null ? "식당(기타)" : CUISINE_SUBCATEGORY_MAP.getOrDefault(cuisine, "식당(기타)");
    } else {
      return null;
    }
    return placeCategoryRepository.findByParentCategoryAndSubCategory("맛집", subCategoryName).orElse(null);
  }

  private List<KakaoLocalPlaceDto> searchAllPages(String region, String categoryGroupCode) {
    List<KakaoLocalPlaceDto> result = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES_PER_QUERY; page++) {
      SearchPage searchPage = searchOnePage(region, categoryGroupCode, page);
      result.addAll(searchPage.items());
      if (searchPage.isEnd()) break;
    }
    return result;
  }

  private record SearchPage(List<KakaoLocalPlaceDto> items, boolean isEnd) {
  }

  private SearchPage searchOnePage(String region, String categoryGroupCode, int page) {
    String url = UriComponentsBuilder.fromUriString(SEARCH_URL)
        .queryParam("query", region)
        .queryParam("category_group_code", categoryGroupCode)
        .queryParam("size", PAGE_SIZE)
        .queryParam("page", page)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    byte[] rawBody = restTemplate.exchange(
        URI.create(url),
        HttpMethod.GET,
        new HttpEntity<>(null, authHeaders()),
        byte[].class
    ).getBody();
    if (rawBody == null || rawBody.length == 0) return new SearchPage(List.of(), true);

    String json = new String(rawBody, StandardCharsets.UTF_8);
    JsonNode root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);

    List<KakaoLocalPlaceDto> items = new ArrayList<>();
    for (JsonNode doc : root.path("documents")) {
      items.add(new KakaoLocalPlaceDto(
          doc.path("place_name").asText(""),
          doc.path("category_name").asText(""),
          doc.path("address_name").asText(""),
          doc.path("road_address_name").asText(""),
          doc.path("x").asText(""),
          doc.path("y").asText("")
      ));
    }
    boolean isEnd = root.path("meta").path("is_end").asBoolean(true);
    return new SearchPage(items, isEnd);
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "KakaoAK " + restApiKey);
    return headers;
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** (업체명+주소) 문자열을 MD5 해시로 변환 - 고유 ID가 없는 데이터의 대체 식별자용 (NaverPlaceSyncService와 동일 패턴) */
  private String hashKey(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return String.valueOf(raw.hashCode());
    }
  }

  private void sleepBriefly() {
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
