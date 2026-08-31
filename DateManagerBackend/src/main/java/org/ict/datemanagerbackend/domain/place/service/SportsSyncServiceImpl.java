package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.SportsMatchDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 서울올림픽기념국민체육진흥공단_체육진흥투표권(스포츠toto,프로토)발매대상 경기정보_GW API
 * (todz_api_tb_match_mgmt_i)로 국내 프로스포츠 "예정" 경기를 places에 채워 넣는 서비스(2026-08-20).
 *
 * <p>이 API는 국내외 리그가 섞여서 내려온다(MLB/EPL 등 해외 리그 포함) - 사용자가 실제로 갈 수 있는
 * 경기장만 의미가 있어서 {@link #DOMESTIC_LEAGUES}로 국내 리그만 거른다. KBO/K리그1/K리그2는 실측으로
 * 확인했고, KBL/V리그는 조회 시점(8월)이 비시즌이라 실제 문자열을 아직 못 봐서 추정치다 - 시즌이
 * 시작되면(KBL 10월~, V리그 겨울) 한 번 실측해서 정확한 표기로 맞춰야 한다.
 *
 * <p>경기장명(stdm_han_nm)만 내려오고 좌표가 없어서, 카카오 로컬 API(KakaoPlaceSyncService와 같은
 * 인증 방식)로 경기장명을 검색해 좌표/주소를 보강한다. 검색이 실패해도(경기장명 표기가 카카오맵과
 * 안 맞는 등) 장소 자체는 만든다 - 큐레이션 탭의 카테고리 목록 조회는 좌표가 없어도 동작하고
 * (searchAll/searchByCategoryIn), "내 주변만 보기" 같은 좌표 기반 기능에서만 안 보일 뿐이다.
 *
 * <p>이미 끝난 경기(match_end_val이 채워진 경기)는 스킵한다 - 데이트 코스 추천 관점에서 이미 끝난
 * 경기는 갈 수 없어서 의미가 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SportsSyncServiceImpl implements SportsSyncService {

  private static final String MATCH_LIST_URL =
      "https://apis.data.go.kr/B551014/SRVC_OD_API_TB_SOSFO_MATCH_MGMT/todz_api_tb_match_mgmt_i";
  private static final String KAKAO_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
  private static final String EXTERNAL_SOURCE = "SPORTSTOTO";
  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 50; // 안전장치 - 최대 5000건까지

  // 실제로 방문 가능한 국내 리그만. KBL/V리그는 실측 전 추정치(위 클래스 주석 참고).
  private static final Set<String> DOMESTIC_LEAGUES = Set.of("KBO", "K리그1", "K리그2", "KBL", "V리그");

  // match_sport_han_nm(종목명) -> PlaceCategory 세부분류 이름. PlaceCategorySeeder에 같은 이름으로
  // 등록해둬야 한다.
  private static final Map<String, String> SPORT_TO_SUBCATEGORY = Map.of(
      "야구", "야구 직관",
      "축구", "축구 직관",
      "농구", "농구 직관",
      "배구", "배구 직관"
  );

  // 경기장명(부분 일치) -> 대표사진 URL(위키미디어 커먼즈, 2026-08-28). 카카오 로컬 API는 좌표/주소만
  // 주고 사진이 없어서, 국내 프로스포츠 경기장은 종류가 한정적이라는 점을 이용해 직접 조성했다.
  // 순서 무관하게 stadiumName에 키가 포함되면 매칭되므로, "대구iM뱅크파크"(축구)와
  // "대구삼성라이온즈파크"(야구)처럼 헷갈릴 수 있는 것들은 겹치지 않게 충분히 구체적인 키를 썼다.
  // KBL 일부(창원체육관·울산동천체육관·부산사직체육관)와 V리그 다수, 안양빙상장(아이스하키)은
  // 위키 문서/사진 자체가 없어서 비워둠 - 추후 다른 소스로 보강 필요.
  private static final Map<String, String> STADIUM_IMAGES = Map.ofEntries(
      // KBO
      Map.entry("잠실야구장", "https://upload.wikimedia.org/wikipedia/commons/f/fa/Jamsil_Baseball_Stadium_panorama_%28April_28_2017%29.jpg"),
      Map.entry("고척스카이돔", "https://upload.wikimedia.org/wikipedia/ko/4/44/%EA%B3%A0%EC%B2%99%EC%8A%A4%EC%B9%B4%EC%9D%B4%EB%8F%94.jpg"),
      Map.entry("인천SSG랜더스필드", "https://upload.wikimedia.org/wikipedia/commons/e/ef/20240504_IncheonSSGLandersField.jpg"),
      Map.entry("수원KT위즈파크", "https://upload.wikimedia.org/wikipedia/commons/b/b7/20150531_KT_Wiz_vs_Doosan_Bears_%282%29.jpg"),
      Map.entry("대전한화생명볼파크", "https://upload.wikimedia.org/wikipedia/commons/8/85/Daejeon_hanwha_Life_Ballpark_2025.jpg"),
      Map.entry("광주기아챔피언스필드", "https://upload.wikimedia.org/wikipedia/commons/5/5c/Gwangju_Kia_Champions_Field_View_04.jpg"),
      Map.entry("대구삼성라이온즈파크", "https://upload.wikimedia.org/wikipedia/commons/9/91/Daegu_Samseong_Lions_Park.jpg"),
      Map.entry("사직야구장", "https://upload.wikimedia.org/wikipedia/commons/5/5a/Busan_Sajik_Stadium_20080706.JPG"),
      Map.entry("창원NC파크", "https://upload.wikimedia.org/wikipedia/commons/b/b9/Chanwon_NC_Park.jpg"),
      // K리그
      Map.entry("전주월드컵경기장", "https://upload.wikimedia.org/wikipedia/commons/f/fa/Jeonju_World_Cup_Stadium_2016.jpg"),
      Map.entry("울산문수축구경기장", "https://upload.wikimedia.org/wikipedia/commons/b/b2/Munsu_20121110_204310_5.jpg"),
      Map.entry("포항스틸야드", "https://upload.wikimedia.org/wikipedia/commons/0/09/Pohang080413_1.jpg"),
      Map.entry("iM뱅크파크", "https://upload.wikimedia.org/wikipedia/commons/1/1d/Daegu_DGB_Bank_Park_2019.jpg"),
      Map.entry("인천축구전용경기장", "https://upload.wikimedia.org/wikipedia/commons/8/8f/Incheon_Soccer_Stadium_2.JPG"),
      Map.entry("제주월드컵경기장", "https://upload.wikimedia.org/wikipedia/commons/a/af/Jejuwcstadium.jpg"),
      Map.entry("강릉종합운동장", "https://upload.wikimedia.org/wikipedia/commons/9/9c/Gangneung_Stadium2.jpg"),
      Map.entry("대전월드컵경기장", "https://upload.wikimedia.org/wikipedia/commons/7/76/Daejeon_World_Cup_Stadium.JPG"),
      Map.entry("수원월드컵경기장", "https://upload.wikimedia.org/wikipedia/ko/6/62/Night_Scenary_of_BigBird.jpg"),
      Map.entry("서울월드컵경기장", "https://upload.wikimedia.org/wikipedia/commons/b/b8/AFC_Champions_League_Final_1st_leg.jpg"),
      Map.entry("김천종합운동장", "https://upload.wikimedia.org/wikipedia/commons/1/18/Gimcheon-Stadion.png"),
      Map.entry("탄천종합운동장", "https://upload.wikimedia.org/wikipedia/commons/b/bb/Tanchon20100223_1.JPG"),
      Map.entry("구덕운동장", "https://upload.wikimedia.org/wikipedia/commons/4/42/Gudeok_Stadium_3.JPG"),
      Map.entry("이순신종합운동장", "https://upload.wikimedia.org/wikipedia/commons/4/45/Yi_Sun-sin_Stadium.JPG"),
      Map.entry("청주종합경기장", "https://upload.wikimedia.org/wikipedia/ko/a/ae/%EC%B2%AD%EC%A3%BC_%EC%A2%85%ED%95%A9_%EA%B2%BD%EA%B8%B0%EC%9E%A54.jpg"),
      Map.entry("목동", "https://upload.wikimedia.org/wikipedia/commons/f/f4/Mokdong_Stadium3.JPG"),
      // KBL
      Map.entry("고양체육관", "https://upload.wikimedia.org/wikipedia/commons/f/f7/Goyang_Gymnasium.png"),
      Map.entry("안양체육관", "https://upload.wikimedia.org/wikipedia/commons/0/0b/Anyang_Gymnasium.png"),
      Map.entry("원주종합체육관", "https://upload.wikimedia.org/wikipedia/commons/c/c3/231226_%EC%9B%90%EC%A3%BC%EC%A2%85%ED%95%A9%EC%B2%B4%EC%9C%A1%EA%B4%80.jpg"),
      // V리그
      Map.entry("삼산월드체육관", "https://upload.wikimedia.org/wikipedia/commons/f/f0/Samsan_World_Gymnasium.png"),
      Map.entry("충무체육관", "https://upload.wikimedia.org/wikipedia/commons/e/e3/%EC%B6%A9%EB%AC%B4%EC%B2%B4%EC%9C%A1%EA%B4%80.jpg")
  );

  private static final DateTimeFormatter MATCH_YMD_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final PlaceRepository placeRepository;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${sportstoto.service-key}")
  private String serviceKey;

  @Value("${kakao.rest-api-key}")
  private String kakaoRestApiKey;

  /** 매일 새벽 4시 45분(TourAPI 4시, 박물관 4시30분과 안 겹치게). */
  @Scheduled(cron = "0 45 4 * * *")
  @Override
  public void syncPlaces() {
    int created = 0;
    int updated = 0;
    int skippedFinished = 0;
    int skippedForeignLeague = 0;
    int skippedGeocodeFailed = 0;

    for (SportsMatchDto match : fetchMatches()) {
      String externalId = String.join("_", match.matchYmd(), match.matchTm(), match.homeTeam(), match.awayTeam());
      Optional<Place> existing = placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, externalId);

      // 경기가 끝나면(match_end_val이 채워지면) 데이트 장소로서 의미가 없어진다. 예전엔 여기서 그냥
      // continue만 해서, 예정 상태로 이미 만들어둔 place가 경기가 끝난 뒤에도 "경기예정"인 채로
      // 영원히 남는 버그가 있었다(2026-08-20 발견) - 매일 재동기화해도 이 매치는 계속 스킵되기만
      // 하고 갱신도 삭제도 안 됐기 때문. 이제 이미 만들어둔 place가 있으면 지워서 정리한다.
      if (match.matchResult() != null && !match.matchResult().isBlank()) {
        skippedFinished++;
        existing.ifPresent(this::deletePlace);
        continue;
      }
      if (!DOMESTIC_LEAGUES.contains(match.leagueName())) {
        skippedForeignLeague++;
        continue;
      }

      String subCategoryName = SPORT_TO_SUBCATEGORY.get(match.sportName());
      PlaceCategory placeCategory = subCategoryName == null ? null
          : placeCategoryRepository.findByParentCategoryAndSubCategory("스포츠", subCategoryName).orElse(null);

      GeocodeResult geo = geocodeStadium(match.stadiumName());
      if (geo == null) {
        skippedGeocodeFailed++;
      }

      String name = match.homeTeam() + " vs " + match.awayTeam();
      LocalDate matchDate = parseMatchDate(match.matchYmd());
      String showTimeInfo = formatMatchTime(match.matchTm());
      String stadiumImage = resolveStadiumImage(match.stadiumName());

      if (existing.isPresent()) {
        Place place = existing.get();
        place.setName(name);
        place.setCategory("스포츠");
        place.setPlaceCategory(placeCategory);
        place.setAddress(geo != null ? geo.address() : match.stadiumName());
        place.setLatitude(geo != null ? geo.lat() : null);
        place.setLongitude(geo != null ? geo.lng() : null);
        place.setStartDate(matchDate);
        place.setShowTimeInfo(showTimeInfo);
        place.setPerformanceState("경기예정");
        if (place.getImageUrl() == null && stadiumImage != null) {
          place.setImageUrl(stadiumImage);
        }
        placeRepository.save(place);
        updated++;
      } else {
        Place place = Place.builder()
            .name(name)
            .category("스포츠")
            .placeCategory(placeCategory)
            .address(geo != null ? geo.address() : match.stadiumName())
            .latitude(geo != null ? geo.lat() : null)
            .longitude(geo != null ? geo.lng() : null)
            .imageUrl(stadiumImage)
            .externalSource(EXTERNAL_SOURCE)
            .externalId(externalId)
            .startDate(matchDate)
            .showTimeInfo(showTimeInfo)
            .performanceState("경기예정")
            .build();
        placeRepository.save(place);
        created++;
      }
    }

    log.info("스포츠 경기 동기화 완료 - 신규 {}건, 갱신 {}건, 종료된 경기라 제외 {}건, 해외 리그라 제외 {}건, "
            + "경기장 좌표 검색 실패 {}건", created, updated, skippedFinished, skippedForeignLeague, skippedGeocodeFailed);
  }

  // 경기가 끝난 매치의 place를 정리한다. cleanupBlacklistedPlaces와 같은 이유로 place_style을 먼저
  // 지우고(1:1로 딸려있을 수 있음), course_items 등 다른 곳에서 이미 참조 중이면(실사용 데이터가
  // 생겼다는 뜻) FK 위반이 나므로 억지로 지우지 않고 조용히 넘어간다.
  private void deletePlace(Place place) {
    try {
      placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
      placeRepository.delete(place);
      placeRepository.flush();
    } catch (DataIntegrityViolationException e) {
      log.info("경기 종료된 place 삭제 건너뜀(다른 곳에서 참조 중) - placeId={}", place.getId());
    }
  }

  private LocalDate parseMatchDate(String matchYmd) {
    if (matchYmd == null || matchYmd.isBlank()) return null;
    try {
      return LocalDate.parse(matchYmd, MATCH_YMD_FORMAT);
    } catch (Exception e) {
      return null;
    }
  }

  // match_tm은 "0135" 같은 HHmm 문자열로 내려온다. "13:5" 처럼 어색하게 안 보이게 앞자리를 채운다.
  private String formatMatchTime(String matchTm) {
    if (matchTm == null || matchTm.length() != 4) return matchTm;
    return matchTm.substring(0, 2) + ":" + matchTm.substring(2);
  }

  /**
   * 경기 목록 전체 조회. TourApiSyncService와 같은 이유로 serviceKey를 URLEncoder로 직접 인코딩한 뒤
   * build(true)로 이중 인코딩을 막는다(+,/,= 포함된 공공데이터포털 키의 흔한 함정).
   */
  private List<SportsMatchDto> fetchMatches() {
    List<SportsMatchDto> all = new ArrayList<>();
    String encodedServiceKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);

    for (int page = 1; page <= MAX_PAGES; page++) {
      String url = UriComponentsBuilder.fromUriString(MATCH_LIST_URL)
          .queryParam("serviceKey", encodedServiceKey)
          .queryParam("pageNo", page)
          .queryParam("numOfRows", PAGE_SIZE)
          .queryParam("resultType", "json")
          .build(true)
          .toUriString();

      List<SportsMatchDto> pageItems;
      try {
        // 이 API는 Content-Type을 "text/json"으로 내려줘서(2026-08-20 실측), Spring 기본
        // HttpMessageConverter가 JSON으로 인식을 못 하고 UnknownContentTypeException을 던진다.
        // KakaoPlaceSyncService와 같은 방식으로 바이트로 받아서 직접 JSON으로 파싱해 우회한다.
        byte[] rawBody = restTemplate.getForObject(URI.create(url), byte[].class);
        if (rawBody == null || rawBody.length == 0) break;
        String json = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);

        JsonNode items = root.path("response").path("body").path("items").path("item");
        pageItems = new ArrayList<>();
        for (JsonNode item : items) {
          pageItems.add(new SportsMatchDto(
              item.path("match_ymd").asText(""),
              item.path("match_tm").asText(""),
              item.path("match_sport_han_nm").asText(""),
              item.path("leag_han_nm").asText(""),
              item.path("hteam_han_nm").asText(""),
              item.path("ateam_han_nm").asText(""),
              item.path("stdm_han_nm").asText(""),
              item.path("match_end_val").asText("")
          ));
        }
      } catch (Exception e) {
        log.error("스포츠 경기정보 API 호출 실패 (page={})", page, e);
        break;
      }

      if (pageItems.isEmpty()) break;
      all.addAll(pageItems);
      if (pageItems.size() < PAGE_SIZE) break;
    }

    return all;
  }

  // 경기장명(부분 일치)으로 STADIUM_IMAGES에서 대표사진을 찾는다. API가 내려주는 stdm_han_nm 표기가
  // 위키 문서 제목과 정확히 안 맞을 수 있어(예: "잠실종합운동장 야구장" vs 우리 키 "잠실야구장")
  // contains()로 느슨하게 매칭한다.
  private String resolveStadiumImage(String stadiumName) {
    if (stadiumName == null) return null;
    for (Map.Entry<String, String> entry : STADIUM_IMAGES.entrySet()) {
      if (stadiumName.contains(entry.getKey()) || entry.getKey().contains(stadiumName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private record GeocodeResult(Double lat, Double lng, String address) {
  }

  // 경기장명을 카카오 로컬 API로 검색해서 첫 결과의 좌표/도로명주소를 가져온다. 결과가 없으면 null.
  private GeocodeResult geocodeStadium(String stadiumName) {
    if (stadiumName == null || stadiumName.isBlank()) return null;

    String url = UriComponentsBuilder.fromUriString(KAKAO_SEARCH_URL)
        .queryParam("query", stadiumName)
        .queryParam("size", 1)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.add("Authorization", "KakaoAK " + kakaoRestApiKey);

      JsonNode root = restTemplate.exchange(
          URI.create(url), HttpMethod.GET, new HttpEntity<>(null, headers), JsonNode.class
      ).getBody();
      if (root == null) return null;

      JsonNode first = root.path("documents").get(0);
      if (first == null) return null;

      Double lng = parseCoordinate(first.path("x").asText(""));
      Double lat = parseCoordinate(first.path("y").asText(""));
      String roadAddress = first.path("road_address_name").asText("");
      String address = roadAddress.isBlank() ? first.path("address_name").asText("") : roadAddress;
      return new GeocodeResult(lat, lng, address.isBlank() ? null : address);
    } catch (Exception e) {
      log.warn("경기장 좌표 검색 실패 (stadiumName={})", stadiumName, e);
      return null;
    }
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
