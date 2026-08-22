package org.ict.datemanagerbackend.domain.place.controller;

import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.CurationPlaceDto;
import org.ict.datemanagerbackend.domain.place.dto.PlaceResponseDto;
import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.place.repository.PerformanceRankingRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceAmenityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRealityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.ict.datemanagerbackend.weather.service.WeatherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// KOPIS/TourAPI 동기화로 채워진 places 데이터를 큐레이션·코스빌더 화면에 내려주는 조회 API.
// 로그인 없이 보이는 홈 탭 추천 카드에서도 쓰이므로 인증 없이 공개한다(SecurityConfig에서 permitAll 처리).
@RestController
@RequestMapping("/api/places")
@Slf4j
public class PlaceController {

  // 지역 통합(2026-08-19) - 충북/충남은 대전, 경북/경남은 대구 칩 하나로 묶어서 보여준다. 실제로는
  // 대전/대구 자체 데이터가 아니라 충청북도/충청남도/경상북도/경상남도(정식 행정구역명) 데이터가
  // 훨씬 많은데(각 2,800~6,000건), 두 지역씩 4개 칩으로 쪼개기엔 앱 성격상 너무 세분화된다고 판단해
  // "대전"/"대구"를 고르면 그 지역들 데이터까지 같이 보여주는 방식으로 합쳤다.
  // "전남" 단독 항목도 사실은 통합이 아니라 별칭(alias)이다 - 실제 주소는 전부 "전라남도"로
  // 저장되는데 칩 값은 줄임말 "전남"이라 그동안 이 칩을 눌러도 0건이 나오고 있었다(2026-08-19,
  // 사용자가 실제 화면에서 "전남 데이터가 없다"고 발견 - 다른 4개 지역과 똑같은 매칭 버그였는데
  // 이것만 고칠 때 놓쳤었다). 전북은 실제 주소가 "전북특별자치도"로 바뀌어 있어서 우연히 "전북"
  // 줄임말과 그대로 겹쳐 이 버그를 안 겪었다.
  private static final Map<String, List<String>> MERGED_REGION_KEYWORDS = Map.of(
      "대전", List.of("대전", "충청북도", "충청남도"),
      "대구", List.of("대구", "경상북도", "경상남도"),
      "전남", List.of("전라남도")
  );

  // region 하나를 최대 3개의 address LIKE 키워드로 펼친다. 통합 대상이 아니면 그 값 하나만 담긴
  // 리스트를 돌려준다 - 호출부는 통합 여부를 신경 쓸 필요 없이 항상 이 결과만 쓰면 된다.
  private static List<String> expandRegion(String region) {
    return MERGED_REGION_KEYWORDS.getOrDefault(region, List.of(region));
  }

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceRealityRepository placeRealityRepository;
  private final PlaceAmenityRepository placeAmenityRepository;
  private final PerformanceRankingRepository performanceRankingRepository;
  private final WeatherService weatherService;
  private final org.ict.datemanagerbackend.domain.user.service.UserStyleUpdateService userStyleUpdateService;
  private final org.ict.datemanagerbackend.domain.place.repository.PlaceLikeRepository placeLikeRepository;
  private final org.ict.datemanagerbackend.domain.user.repository.UserRepository userRepository;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${kakao.rest-api-key}")
  private String kakaoRestApiKey;

  // 홈탭 "내 주변"에서 좌표 최근접 풀링 대신 "같은 시/도" 매칭을 써야 하는 희소 카테고리(2026-08-20).
  // 스포츠는 전국 몇십~몇백 곳뿐이라 최근접 N개 풀에 아예 안 걸리는 문제가 있었음
  // (PlaceRepository.findSparseByCategoryAndAddress 주석 참고). "스포츠"는 대분류 자체가 희소해서
  // 세부분류 구분 없이 항상 이 방식을 쓰고, "공연"은 대분류 전체로는 흔하지만(연극/뮤지컬 등 합쳐
  // 수천 건) 그중 대형 콘서트·뮤직페스티벌만 떼어보면 똑같이 희소(전국 471건/36건, 2026-08-20 실측)
  // 하므로 subCategory가 이 두 값 중 하나일 때만 같은 방식을 적용한다.
  private static final Set<String> SPARSE_NEARBY_CATEGORIES = Set.of("스포츠");
  private static final Set<String> SPARSE_PERFORMANCE_SUBCATEGORIES =
      Set.of("대형 콘서트(아이돌/스타디움)", "뮤직페스티벌");

  public PlaceController(
      PlaceRepository placeRepository,
      PlaceStyleRepository placeStyleRepository,
      PlaceRealityRepository placeRealityRepository,
      PlaceAmenityRepository placeAmenityRepository,
      PerformanceRankingRepository performanceRankingRepository,
      WeatherService weatherService,
      org.ict.datemanagerbackend.domain.user.service.UserStyleUpdateService userStyleUpdateService,
      org.ict.datemanagerbackend.domain.place.repository.PlaceLikeRepository placeLikeRepository,
      org.ict.datemanagerbackend.domain.user.repository.UserRepository userRepository,
      PlaceCategoryRepository placeCategoryRepository) {
    this.placeRepository = placeRepository;
    this.placeStyleRepository = placeStyleRepository;
    this.placeRealityRepository = placeRealityRepository;
    this.placeAmenityRepository = placeAmenityRepository;
    this.performanceRankingRepository = performanceRankingRepository;
    this.weatherService = weatherService;
    this.userStyleUpdateService = userStyleUpdateService;
    this.placeLikeRepository = placeLikeRepository;
    this.userRepository = userRepository;
    this.placeCategoryRepository = placeCategoryRepository;
  }

  // 큐레이션 탭(데이트/숙박 카드)용 조회 API. matchScore는 아직 항상 null - 로그인 유저 성향값을
  // 저장하는 파이프라인이 없어서(Task #20) 실제 매칭 계산을 못 붙인 상태. 그동안은 최신순(id desc)으로
  // 대체 정렬한다.
  // category는 콤마로 여러 값을 묶어 보낼 수 있다 - "공연" 칩처럼 실제 DB 값이 여러 개(장르명)로
  // 나뉘어 있는 경우를 프론트가 한 번에 필터링하기 위해서다(2026-08-14).
  // region은 지역 필터용(2026-08-19) - 별도 시/도 컬럼이 없어서 address에 이 문자열이 포함되는
  // 장소만 남긴다(예: region=서울 -> "서울특별시 ..." 주소만 통과).
  // keyword는 장소 이름 검색용(2026-08-19) - 지역/카테고리 필터와 AND로 조합된다(예: 제주 지역
  // 필터를 걸어둔 채로 검색하면 제주 안에서만 이름이 매치되는 장소를 찾는다).
  // district는 서울/경기/부산 등에서 한 단계 더 좁히는 구/시 필터(예: "중구") - "중구"/"동구"/
  // "고성군" 같은 이름은 여러 시/도가 똑같이 쓰기 때문에, 이 값 하나만으로는 다른 지역이 섞여
  // 들어올 수 있어 반드시 region과 함께 AND로 넘겨야 한다(PlaceRepository.searchByCategoryIn 참고).
  // energyTarget(2026-08-20): 큐레이션 탭 에너지 게이지(0~100) - 값이 있으면 scoreEnergy가 가까운
  // 순으로 정렬됨(PlaceRepository 주석 참고).
  // lat/lon(2026-08-20): 있으면 그 위치의 실시간 날씨를 확인해서 비가 오면 실외 장소를 제외하고,
  // 폭염/한파면 실내 장소를 우선 정렬한다. 위치 권한이 없으면(null) 날씨 조건 없이 기존 동작 그대로.
  @GetMapping("/curation")
  public ResponseEntity<Page<CurationPlaceDto>> listCurationPlaces(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String subCategory,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer energyTarget,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lon,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    boolean hasCategory = category != null && !category.isBlank();
    boolean hasSubCategory = subCategory != null && !subCategory.isBlank();
    List<String> categories = hasCategory ? List.of(category.split(",")) : null;

    // region이 없으면 빈 문자열을 r1으로 넘긴다 - address LIKE '%%'는 모든 행에 걸리므로
    // "지역 필터 없음"과 같은 효과를 낸다(PlaceRepository.searchByCategoryIn 주석 참고).
    List<String> regionKeywords = (region == null || region.isBlank()) ? List.of("") : expandRegion(region);
    String r1 = regionKeywords.get(0);
    String r2 = regionKeywords.size() > 1 ? regionKeywords.get(1) : null;
    String r3 = regionKeywords.size() > 2 ? regionKeywords.get(2) : null;
    String districtFilter = (district == null || district.isBlank()) ? null : district;
    String keywordFilter = (keyword == null || keyword.isBlank()) ? null : keyword;

    boolean excludeOutdoor = false;
    boolean indoorBoost = false;
    if (lat != null && lon != null) {
      try {
        WeatherService.CurationWeatherSignal weather = weatherService.getCurationWeatherSignal(lat, lon);
        excludeOutdoor = weather.raining();
        indoorBoost = weather.extremeTemp();
      } catch (Exception e) {
        // 날씨 조회 실패해도 큐레이션 목록 자체는 정상적으로 내려줘야 하므로(챗봇 컨텍스트 조회와 같은 이유) 무시
      }
    }

    Page<Place> page = hasCategory
        ? placeRepository.searchByCategoryIn(
            categories, hasSubCategory ? subCategory : null, r1, r2, r3, districtFilter, keywordFilter,
            excludeOutdoor, indoorBoost, energyTarget, pageable)
        : placeRepository.searchAll(
            r1, r2, r3, districtFilter, keywordFilter, excludeOutdoor, indoorBoost, energyTarget, pageable);

    List<Long> placeIds = page.getContent().stream().map(Place::getId).toList();

    Map<Long, PlaceReality> realityByPlaceId = placeRealityRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(r -> r.getPlace().getId(), r -> r));

    Map<Long, List<String>> amenitiesByPlaceId = placeAmenityRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.groupingBy(
            a -> a.getPlace().getId(),
            Collectors.mapping(PlaceAmenity::getAmenityTag, Collectors.toList())
        ));

    Map<Long, PerformanceRanking> rankingByPlaceId = performanceRankingRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(r -> r.getPlace().getId(), r -> r));

    Map<Long, PlaceStyle> styleByPlaceId = placeStyleRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(s -> s.getPlace().getId(), s -> s));

    return ResponseEntity.ok(page.map(place -> {
      PlaceCategory placeCategory = place.getPlaceCategory();
      PlaceReality reality = realityByPlaceId.get(place.getId());
      List<String> amenities = amenitiesByPlaceId.getOrDefault(place.getId(), List.of());
      PerformanceRanking ranking = rankingByPlaceId.get(place.getId());
      PlaceStyle style = styleByPlaceId.get(place.getId());
      return CurationPlaceDto.from(place, placeCategory, reality, amenities, ranking, style);
    }));
  }

  // category를 안 넘기면 전체를 최신 등록순으로, 넘기면 해당 카테고리만 조회한다.
  @GetMapping
  public ResponseEntity<Page<PlaceResponseDto>> listPlaces(
      @RequestParam(required = false) String category,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<Place> page = (category == null || category.isBlank())
        ? placeRepository.findAll(pageable)
        : placeRepository.findByCategory(category, pageable);

    // 이 페이지에 담긴 place id들의 PlaceStyle을 한 번에 조회해서 id로 바로 찾을 수 있는 Map으로 만든다
    // (장소 하나마다 따로 쿼리하면 N+1 문제가 생기기 때문).
    List<Long> placeIds = page.getContent().stream().map(Place::getId).toList();
    Map<Long, PlaceStyle> styleByPlaceId = placeStyleRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(s -> s.getPlace().getId(), s -> s));

    return ResponseEntity.ok(page.map(place -> PlaceResponseDto.from(place, styleByPlaceId.get(place.getId()))));
  }

  // 홈탭 "지역 기반 추천"용 - 접속 좌표(lat/lon)에서 가까운 순으로 최대 limit개를 내려준다.
  // 페이지네이션 없이 그냥 리스트로 반환(추천 후보 풀로만 쓰이므로 단순하게).
  // category는 콤마로 여러 값을 묶어 보낼 수 있다(큐레이션 탭과 동일한 이유 - "공연" 칩은 실제로는
  // 장르명 여러 개를 가리켜야 함, 2026-08-14). findNearestPlaces가 카테고리 필터를 지원하지 않아서,
  // 필터를 줄 때는 넉넉한 풀(limit의 5배, 최소 1000)을 먼저 가까운 순으로 가져온 뒤 걸러낸다 - 이미
  // 거리순으로 온 리스트라 필터링해도 순서는 유지된다.
  @GetMapping("/nearby")
  public ResponseEntity<List<PlaceResponseDto>> listNearbyPlaces(
      @RequestParam double lat,
      @RequestParam double lon,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String subCategory,
      @RequestParam(defaultValue = "300") int limit) {
    boolean sparsePerformance = "공연".equals(category)
        && subCategory != null && SPARSE_PERFORMANCE_SUBCATEGORIES.contains(subCategory);
    List<Place> places;
    if (category != null && (SPARSE_NEARBY_CATEGORIES.contains(category) || sparsePerformance)) {
      String region = reverseGeocodeRegion(lat, lon);
      log.info("희소 카테고리 내 주변 조회 - category={}, subCategory={}, region={}", category, subCategory, region);
      places = region == null
          ? List.of() // 역지오코딩 실패(카카오 API 오류 등) 시 엉뚱한 전국 결과를 섞어 보여주지 않고 빈 목록
          : placeRepository.findSparseByCategoryAndAddress(category, subCategory, region).stream()
              .limit(limit)
              .toList();
      log.info("희소 카테고리 조회 결과 {}건", places.size());
    } else if (category == null || category.isBlank()) {
      places = placeRepository.findNearestPlaces(lat, lon, limit);
    } else {
      List<String> categories = List.of(category.split(","));
      int pool = Math.max(limit * 5, 1000);
      places = placeRepository.findNearestPlaces(lat, lon, pool).stream()
          .filter(place -> categories.contains(place.getCategory()))
          .limit(limit)
          .toList();
    }

    List<Long> placeIds = places.stream().map(Place::getId).toList();
    Map<Long, PlaceStyle> styleByPlaceId = placeStyleRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(s -> s.getPlace().getId(), s -> s));
    Map<Long, PerformanceRanking> rankingByPlaceId = performanceRankingRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(r -> r.getPlace().getId(), r -> r));

    List<PlaceResponseDto> result = places.stream()
        .map(place -> PlaceResponseDto.from(place, styleByPlaceId.get(place.getId()), rankingByPlaceId.get(place.getId())))
        .toList();
    return ResponseEntity.ok(result);
  }

  // 카카오 coord2regioncode가 주는 정식 명칭 -> place.address에 실제로 저장된 줄임말(2026-08-20 실측
  // 확인). SportsSyncService가 경기장 좌표를 딸 때 쓰는 카카오 키워드 검색 API(coord2regioncode와는
  // 다른 API)가 도로명주소를 "서울특별시"가 아니라 "서울"처럼 줄임말로 내려줘서, 정식 명칭 그대로
  // LIKE에 쓰면 하나도 안 걸린다(실측 - "서울특별시"로 검색했더니 "서울 송파구..." 주소가 매칭 안 됨).
  private static final Map<String, String> OFFICIAL_TO_SHORT_REGION_NAME = Map.ofEntries(
      Map.entry("서울특별시", "서울"), Map.entry("부산광역시", "부산"), Map.entry("대구광역시", "대구"),
      Map.entry("인천광역시", "인천"), Map.entry("광주광역시", "광주"), Map.entry("대전광역시", "대전"),
      Map.entry("울산광역시", "울산"), Map.entry("세종특별자치시", "세종"), Map.entry("경기도", "경기"),
      Map.entry("강원특별자치도", "강원"), Map.entry("충청북도", "충북"), Map.entry("충청남도", "충남"),
      Map.entry("전북특별자치도", "전북"), Map.entry("전라남도", "전남"), Map.entry("경상북도", "경북"),
      Map.entry("경상남도", "경남"), Map.entry("제주특별자치도", "제주")
  );

  // 좌표 -> "시/도" 이름 역지오코딩(2026-08-20, SPARSE_NEARBY_CATEGORIES 처리용). 카카오 로컬 API의
  // coord2regioncode로 region_1depth_name(정식 명칭, 예: "서울특별시")을 받은 뒤, 위 매핑으로 실제
  // 주소에 쓰이는 줄임말로 바꿔서 반환한다. 실패하면 null - 호출부가 빈 목록으로 처리한다(엉뚱한
  // 전국 결과를 섞어 보여주는 것보다 안전).
  private String reverseGeocodeRegion(double lat, double lon) {
    String url = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json")
        .queryParam("x", lon)
        .queryParam("y", lat)
        .toUriString();
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.add("Authorization", "KakaoAK " + kakaoRestApiKey);
      JsonNode root = restTemplate.exchange(
          java.net.URI.create(url), HttpMethod.GET, new HttpEntity<>(null, headers), JsonNode.class
      ).getBody();
      if (root == null) return null;
      JsonNode first = root.path("documents").get(0);
      if (first == null) return null;
      String official = first.path("region_1depth_name").asText("");
      if (official.isBlank()) return null;
      return OFFICIAL_TO_SHORT_REGION_NAME.getOrDefault(official, official);
    } catch (Exception e) {
      log.warn("좌표 역지오코딩 실패 (lat={}, lon={})", lat, lon, e);
      return null;
    }
  }

  // 큐레이션 탭 "내 주변만 보기" 토글용 - findNearestPlaces는 카테고리 필터를 지원하지 않아서, 필터링
  // 여지를 두고 넉넉하게(요청 limit의 20배 또는 최소 500) 가까운 순 풀을 가져온 다음 카테고리를
  // 애플리케이션에서 걸러내고 앞에서부터 limit개만 자른다. 이미 거리순으로 온 리스트라 필터링해도
  // 순서(가까운 순)는 그대로 유지된다(2026-08-14).
  @GetMapping("/curation/nearby")
  public ResponseEntity<List<CurationPlaceDto>> listNearbyCurationPlaces(
      @RequestParam double lat,
      @RequestParam double lon,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String subCategory,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "30") int limit) {
    int pool = Math.max(limit * 20, 500);
    List<Place> candidates = placeRepository.findNearestPlaces(lat, lon, pool);

    List<String> categories = (category == null || category.isBlank())
        ? null
        : List.of(category.split(","));

    List<String> regionKeywords = (region == null || region.isBlank()) ? null : expandRegion(region);

    List<Place> filtered = candidates.stream()
        .filter(place -> categories == null || categories.contains(place.getCategory()))
        .filter(place -> subCategory == null || subCategory.isBlank()
            || (place.getPlaceCategory() != null && subCategory.equals(place.getPlaceCategory().getSubCategory())))
        .filter(place -> regionKeywords == null
            || (place.getAddress() != null && regionKeywords.stream().anyMatch(place.getAddress()::contains)))
        .filter(place -> district == null || district.isBlank()
            || (place.getAddress() != null && place.getAddress().contains(district)))
        .filter(place -> keyword == null || keyword.isBlank()
            || (place.getName() != null && place.getName().contains(keyword)))
        .limit(limit)
        .toList();

    List<Long> placeIds = filtered.stream().map(Place::getId).toList();
    Map<Long, PlaceReality> realityByPlaceId = placeRealityRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(r -> r.getPlace().getId(), r -> r));
    Map<Long, List<String>> amenitiesByPlaceId = placeAmenityRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.groupingBy(
            a -> a.getPlace().getId(),
            Collectors.mapping(PlaceAmenity::getAmenityTag, Collectors.toList())
        ));

    Map<Long, PerformanceRanking> rankingByPlaceId = performanceRankingRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(r -> r.getPlace().getId(), r -> r));

    Map<Long, PlaceStyle> styleByPlaceId = placeStyleRepository.findByPlace_IdIn(placeIds).stream()
        .collect(Collectors.toMap(s -> s.getPlace().getId(), s -> s));

    List<CurationPlaceDto> result = filtered.stream()
        .map(place -> CurationPlaceDto.from(
            place,
            place.getPlaceCategory(),
            realityByPlaceId.get(place.getId()),
            amenitiesByPlaceId.getOrDefault(place.getId(), List.of()),
            rankingByPlaceId.get(place.getId()),
            styleByPlaceId.get(place.getId())
        ))
        .toList();
    return ResponseEntity.ok(result);
  }

  // 큐레이션 탭 카테고리 칩에 개수를 표시하기 위한 공개 API. 카테고리별로 매번 목록을 다 가져와
  // 세는 건 비효율적이라 DB에서 GROUP BY로 바로 집계한다(AdminController의 같은 패턴 재사용).
  // region이 있으면 그 지역 안에서만 집계한다(2026-08-19) - 없으면 예전처럼 전국 집계.
  @GetMapping("/category-counts")
  public ResponseEntity<Map<String, Long>> getCategoryCounts(
      @RequestParam(required = false) String region, @RequestParam(required = false) String district) {
    boolean hasRegion = region != null && !region.isBlank();
    String districtFilter = (district == null || district.isBlank()) ? null : district;
    List<Object[]> rows;
    if (hasRegion) {
      List<String> regionKeywords = expandRegion(region);
      rows = placeRepository.countGroupedByCategoryAndAddressContaining(
          regionKeywords.get(0),
          regionKeywords.size() > 1 ? regionKeywords.get(1) : null,
          regionKeywords.size() > 2 ? regionKeywords.get(2) : null,
          districtFilter);
    } else {
      rows = placeRepository.countGroupedByCategory();
    }

    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    for (Object[] row : rows) {
      counts.put((String) row[0], (Long) row[1]);
    }
    return ResponseEntity.ok(counts);
  }

  // 숙박 탭처럼 대분류 하나를 세부분류 칩으로 한 번 더 쪼개서 보여줄 때 쓰는 개수 집계 API.
  @GetMapping("/category-counts/sub")
  public ResponseEntity<Map<String, Long>> getSubCategoryCounts(
      @RequestParam String category, @RequestParam(required = false) String region,
      @RequestParam(required = false) String district) {
    boolean hasRegion = region != null && !region.isBlank();
    String districtFilter = (district == null || district.isBlank()) ? null : district;
    List<Object[]> rows;
    if (hasRegion) {
      List<String> regionKeywords = expandRegion(region);
      rows = placeRepository.countGroupedBySubCategoryAndAddressContaining(
          category,
          regionKeywords.get(0),
          regionKeywords.size() > 1 ? regionKeywords.get(1) : null,
          regionKeywords.size() > 2 ? regionKeywords.get(2) : null,
          districtFilter);
    } else {
      rows = placeRepository.countGroupedBySubCategory(category);
    }

    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    for (Object[] row : rows) {
      counts.put((String) row[0], (Long) row[1]);
    }
    return ResponseEntity.ok(counts);
  }

  // 대분류 하나의 세부분류 목록(이름+이모지)을 그대로 내려준다(2026-08-22) - 맛집처럼 세부칩 UI를
  // 만들 때, 프론트가 세부분류 목록을 하드코딩해서 들고 있으면(RESTAURANT_SUBCATEGORIES처럼) DB에
  // 세부분류가 추가/변경될 때마다 프론트도 같이 고쳐야 하는 문제가 있었다(실제로 맛집 세부칩을
  // 4개만 하드코딩했다가 실제로는 14개였던 걸 뒤늦게 발견함). 이 API로 프론트가 항상 최신
  // place_categories 그대로를 받아 칩을 그리게 하면 그런 어긋남 자체가 안 생긴다.
  @GetMapping("/categories")
  public ResponseEntity<List<Map<String, String>>> listSubCategories(@RequestParam String parent) {
    List<Map<String, String>> result = placeCategoryRepository.findByParentCategory(parent).stream()
        .map(pc -> Map.of("subCategory", pc.getSubCategory(), "emoji", pc.getEmoji() != null ? pc.getEmoji() : ""))
        .toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getPlace(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    Optional<Place> placeOpt = placeRepository.findById(id);
    if (placeOpt.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "장소를 찾을 수 없습니다"));
    }
    PlaceStyle style = placeStyleRepository.findByPlace_Id(id).orElse(null);
    // 로그인한 유저가 장소 상세를 열람하면 "약한 신호"(α=0.02)로 성향값을 실시간 갱신한다
    // (2026-08-22, backup/user-style 포팅). 비로그인 열람(authentication == null)은 건너뛴다 -
    // 이 엔드포인트는 로그인 없이도 볼 수 있는 공개 API라서.
    if (style != null && authentication != null) {
      try {
        userStyleUpdateService.applyPlaceView((Long) authentication.getPrincipal(), style);
      } catch (Exception e) {
        log.warn("장소 열람 기반 성향값 갱신 실패 (placeId={})", id, e);
      }
    }
    return ResponseEntity.ok(PlaceResponseDto.from(placeOpt.get(), style));
  }

  // 2026-08-22 - 처음엔 찜 목록을 영속화하지 않고 "좋아요 눌렀다"는 신호만 UserStyle 갱신 엔진에
  // 보내는 가벼운 POST 하나였는데, 그러면 새로고침할 때마다 하트가 다 꺼져서 실제 찜 기능으로는
  // 못 쓴다는 문제가 있었다. place_likes 테이블에 실제로 저장/삭제하는 진짜 좋아요로 바꿨다 -
  // 성향값 갱신은 "새로 좋아요를 누른 순간"(이미 좋아요한 걸 또 누르면 무시)에만 트리거되고,
  // 좋아요를 뺄 때는(DELETE) 트리거하지 않는다(연속으로 켰다 껐다 하며 점수를 왜곡시키지 않기 위함).
  @GetMapping("/likes")
  public ResponseEntity<?> getMyLikedPlaceIds(org.springframework.security.core.Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    List<Long> placeIds = placeLikeRepository.findByUser_Id(userId).stream()
        .map(like -> like.getPlace().getId())
        .toList();
    return ResponseEntity.ok(placeIds);
  }

  @PostMapping("/{id}/like")
  public ResponseEntity<?> likePlace(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    if (placeLikeRepository.existsByUser_IdAndPlace_Id(userId, id)) {
      return ResponseEntity.ok(Map.of("success", true, "liked", true)); // 이미 좋아요한 상태 - 그대로 성공 처리, 점수는 다시 안 건드림
    }
    Optional<Place> placeOpt = placeRepository.findById(id);
    if (placeOpt.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "장소를 찾을 수 없습니다"));
    }
    placeLikeRepository.save(org.ict.datemanagerbackend.domain.place.entity.PlaceLike.builder()
        .user(userRepository.getReferenceById(userId))
        .place(placeOpt.get())
        .build());
    PlaceStyle style = placeStyleRepository.findByPlace_Id(id).orElse(null);
    if (style != null) {
      try {
        userStyleUpdateService.applyPlaceLike(userId, style);
      } catch (Exception e) {
        log.warn("좋아요 기반 성향값 갱신 실패 (placeId={})", id, e);
      }
    }
    return ResponseEntity.ok(Map.of("success", true, "liked", true));
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{id}/like")
  public ResponseEntity<?> unlikePlace(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    placeLikeRepository.findByUser_IdAndPlace_Id(userId, id).ifPresent(placeLikeRepository::delete);
    return ResponseEntity.ok(Map.of("success", true, "liked", false));
  }

  @PostMapping("/{id}/course-add")
  public ResponseEntity<?> addPlaceToCourse(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    PlaceStyle style = placeStyleRepository.findByPlace_Id(id).orElse(null);
    if (style == null) {
      return ResponseEntity.status(404).body(Map.of("error", "장소를 찾을 수 없습니다"));
    }
    try {
      userStyleUpdateService.applyCourseAdd((Long) authentication.getPrincipal(), style);
    } catch (Exception e) {
      log.warn("코스 담기 기반 성향값 갱신 실패 (placeId={})", id, e);
    }
    return ResponseEntity.ok(Map.of("success", true));
  }
}
