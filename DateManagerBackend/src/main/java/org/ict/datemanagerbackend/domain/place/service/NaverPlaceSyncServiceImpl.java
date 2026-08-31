package org.ict.datemanagerbackend.domain.place.service;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.NaverLocalPlaceDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 네이버 지역 검색 API(NAVER API HUB, https://naverapihub.apigw.ntruss.com/search/v1/local)에서
 * 장소 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * <p>TourAPI/KOPIS와 달리 "카테고리 전체 목록"을 내려주는 API가 아니라 검색어(query) 기반이고,
 * 실측 결과 한 검색어당 최대 5건까지만 나오며(display 파라미터 상한) start로 다음 페이지를 요청해도
 * 똑같은 첫 페이지가 반복돼서 실질적인 페이징이 안 된다. 그래서 "지역명 + 카테고리 키워드" 조합을
 * 최대한 다양하게 만들어 여러 번 호출하는 방식으로 수집한다.
 *
 * <p>TourAPI/KOPIS로 이미 충분한 맛집/관광지/쇼핑/숙박/공연과 겹치지 않도록, 상대적으로 데이터가
 * 부족했던 액티비티/문화시설 카테고리 보강 용도로만 사용한다.
 *
 * <p>고유 ID가 없는 API라 MuseumSyncService와 동일하게 (업체명+주소) MD5 해시를 external_id로 쓴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverPlaceSyncServiceImpl implements NaverPlaceSyncService {

  private static final String LIST_URL = "https://naverapihub.apigw.ntruss.com/search/v1/local";
  private static final String EXTERNAL_SOURCE = "NAVER";

  // mapx/mapy는 WGS84 좌표에 10^7을 곱한 정수로 내려온다(실측 확인: 1270274938 -> 127.0274938).
  private static final double COORDINATE_SCALE = 1e7;

  // 한 검색어당 최대 5건까지만 나와서(display 상한), 지역명 x 카테고리별 키워드 조합으로 최대한
  // 다양한 검색어를 만들어 여러 번 호출한다. 액티비티/문화시설 수요가 수도권(서울/경기/인천)과
  // 부산에 몰려있다는 판단에 따라 우선 이 지역들부터 커버하고, 부족하면 다른 광역시로 넓힌다.
  //
  // "서구"/"중구"/"남구"/"동구"처럼 같은 구 이름이 여러 도시에 겹치는 경우가 많아(인천 서구, 부산 서구,
  // 광주 서구 등) 시/도 이름 없이 검색하면 엉뚱한 지역 결과가 섞일 수 있다. 그래서 각 지역명 앞에
  // 시/도 이름을 붙여 질의어 자체를 명확하게 만든다(예: "인천 서구", "부산 서구").
  private static final List<String> REGIONS = new ArrayList<>();

  static {
    for (String gu : List.of(
        "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
        "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
        "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"
    )) {
      REGIONS.add("서울 " + gu);
    }
    // 경기도 주요 시 (인구/상권 규모가 큰 곳 위주로 우선 선정)
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

  // 액티비티/문화시설은 TourAPI/KOPIS 수집량이 상대적으로 적었던 카테고리라 보강 대상으로 선정함.
  private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>(Map.of(
      "액티비티", List.of("방탈출", "보드게임카페", "실내클라이밍", "스크린골프", "볼링장", "VR체험관"),
      "문화시설", List.of("전시회", "갤러리", "복합문화공간", "공방체험", "소극장", "북카페")
  ));

  private final PlaceRepository placeRepository;
  private final PlaceDedupService placeDedupService;
  // 타임아웃 미설정 시 네이버 응답이 느려지면 요청이 무한정 붙잡히는 문제가 있어(2026-08-26,
  // NaverCategoryMatchServiceImpl과 동일 원인) 짧게 끊어서 다음으로 넘어가게 한다.
  private final RestTemplate restTemplate = buildRestTemplate();

  private static RestTemplate buildRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }

  @Value("${naver.search.client-id}")
  private String clientId;

  @Value("${naver.search.client-secret}")
  private String clientSecret;

  /**
   * 매주 일요일 새벽 5시에 자동 실행됨 (TourAPI 4시, 박물관 4시30분과 겹치지 않게 배치, 검색어 기반이라
   * 매일 돌릴 필요는 없어 주 1회로 둠).
   */
  @Scheduled(cron = "0 0 5 * * SUN")
  @Override
  public void syncPlaces() {
    // 검색어 기반이라 매 실행마다 결과가 달라질 수 있어 upsert 대신 전체 교체 방식을 쓴다
    // (폐업/이전 등으로 사라진 곳이 계속 남아있는 것도 방지됨).
    placeRepository.deleteByExternalSource(EXTERNAL_SOURCE);

    int created = 0;
    int skippedDuplicate = 0;
    int failedQueries = 0;
    java.util.Set<String> seenThisRun = new java.util.HashSet<>(); // 같은 실행 안에서 같은 장소가 여러 검색어에 걸려 중복 삽입되는 것 방지

    for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
      String category = entry.getKey();

      for (String keyword : entry.getValue()) {
        for (String region : REGIONS) {
          String query = region + " " + keyword;
          List<NaverLocalPlaceDto> items;
          try {
            items = search(query);
          } catch (Exception e) {
            log.error("네이버 지역 검색 실패 (query={})", query, e);
            failedQueries++;
            continue;
          }

          for (NaverLocalPlaceDto item : items) {
            String address = !item.roadAddress().isBlank() ? item.roadAddress() : item.address();
            if (item.title().isBlank() || address.isBlank()) continue;

            Double lng = parseCoordinate(item.mapx());
            Double lat = parseCoordinate(item.mapy());
            String externalId = hashKey(item.title() + "|" + address);

            if (!seenThisRun.add(externalId)) {
              continue; // 이번 실행에서 이미 다른 검색어로 저장한 장소
            }

            // TourAPI/KOPIS/박물관 등 다른 소스에서 이미 저장된 같은 실제 장소인지 확인
            Optional<Place> duplicate = placeDedupService.findDuplicate(item.title(), lat, lng);
            if (duplicate.isPresent()) {
              skippedDuplicate++;
              continue;
            }

            Place place = Place.builder()
                .name(item.title())
                .category(category)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .externalSource(EXTERNAL_SOURCE)
                .externalId(externalId)
                .build();
            placeRepository.save(place);
            created++;
          }

          sleepBriefly(); // API에 부담을 덜 주기 위한 최소한의 호출 간격
        }
      }
    }

    log.info("네이버 지역 검색 동기화 완료 - 신규 {}건, 다른 소스와 중복이라 건너뜀 {}건, 실패한 검색어 {}건",
        created, skippedDuplicate, failedQueries);
  }

  private List<NaverLocalPlaceDto> search(String query) {
    String url = UriComponentsBuilder.fromUriString(LIST_URL)
        .queryParam("query", query)
        .queryParam("display", 5)
        .queryParam("start", 1)
        .queryParam("sort", "comment")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    List<NaverLocalPlaceDto> result = new ArrayList<>();

    // 네이버 API HUB가 실제 바디는 JSON이면서도 Content-Type을 "text/plain;charset=UTF-8"로 내려줘서
    // (application/json이 아님) Jackson의 컨텐츠타입 기반 컨버터 선택이 어긋나 한글이 깨지는 문제가 있었다.
    // 그래서 바이트로 그대로 받은 뒤 UTF-8로 직접 디코딩해서 파싱한다(컨버터의 자동 charset 추정을 안 거침).
    byte[] rawBody = restTemplate.exchange(
        java.net.URI.create(url),
        org.springframework.http.HttpMethod.GET,
        new org.springframework.http.HttpEntity<>(null, authHeaders()),
        byte[].class
    ).getBody();
    if (rawBody == null || rawBody.length == 0) return result;

    String json = new String(rawBody, StandardCharsets.UTF_8);
    JsonNode root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);

    JsonNode items = root.path("items");
    for (JsonNode item : items) {
      result.add(new NaverLocalPlaceDto(
          stripHtml(item.path("title").asText("")),
          item.path("address").asText(""),
          item.path("roadAddress").asText(""),
          item.path("mapx").asText(""),
          item.path("mapy").asText("")
      ));
    }
    return result;
  }

  private org.springframework.http.HttpHeaders authHeaders() {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add("X-NCP-APIGW-API-KEY-ID", clientId);
    headers.add("X-NCP-APIGW-API-KEY", clientSecret);
    return headers;
  }

  // 네이버가 검색어와 일치하는 부분을 <b>...</b>로 감싸서 내려주므로 태그만 제거한다.
  private String stripHtml(String value) {
    return value.replaceAll("</?b>", "").trim();
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value) / COORDINATE_SCALE;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** (업체명+주소) 문자열을 MD5 해시로 변환 - 고유 ID가 없는 데이터의 대체 식별자용 (MuseumSyncService와 동일 패턴) */
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
