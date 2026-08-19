package org.ict.datemanagerbackend.domain.place.controller;

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
import org.ict.datemanagerbackend.domain.place.repository.PlaceRealityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// KOPIS/TourAPI 동기화로 채워진 places 데이터를 큐레이션·코스빌더 화면에 내려주는 조회 API.
// 로그인 없이 보이는 홈 탭 추천 카드에서도 쓰이므로 인증 없이 공개한다(SecurityConfig에서 permitAll 처리).
@RestController
@RequestMapping("/api/places")
public class PlaceController {

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceRealityRepository placeRealityRepository;
  private final PlaceAmenityRepository placeAmenityRepository;
  private final PerformanceRankingRepository performanceRankingRepository;

  public PlaceController(
      PlaceRepository placeRepository,
      PlaceStyleRepository placeStyleRepository,
      PlaceRealityRepository placeRealityRepository,
      PlaceAmenityRepository placeAmenityRepository,
      PerformanceRankingRepository performanceRankingRepository) {
    this.placeRepository = placeRepository;
    this.placeStyleRepository = placeStyleRepository;
    this.placeRealityRepository = placeRealityRepository;
    this.placeAmenityRepository = placeAmenityRepository;
    this.performanceRankingRepository = performanceRankingRepository;
  }

  // 큐레이션 탭(데이트/숙박 카드)용 조회 API. matchScore는 아직 항상 null - 로그인 유저 성향값을
  // 저장하는 파이프라인이 없어서(Task #20) 실제 매칭 계산을 못 붙인 상태. 그동안은 최신순(id desc)으로
  // 대체 정렬한다.
  // category는 콤마로 여러 값을 묶어 보낼 수 있다 - "공연" 칩처럼 실제 DB 값이 여러 개(장르명)로
  // 나뉘어 있는 경우를 프론트가 한 번에 필터링하기 위해서다(2026-08-14).
  // region은 지역 필터용(2026-08-19) - 별도 시/도 컬럼이 없어서 address에 이 문자열이 포함되는
  // 장소만 남긴다(예: region=서울 -> "서울특별시 ..." 주소만 통과).
  @GetMapping("/curation")
  public ResponseEntity<Page<CurationPlaceDto>> listCurationPlaces(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String subCategory,
      @RequestParam(required = false) String region,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    boolean hasRegion = region != null && !region.isBlank();
    boolean hasCategory = category != null && !category.isBlank();
    boolean hasSubCategory = subCategory != null && !subCategory.isBlank();
    List<String> categories = hasCategory ? List.of(category.split(",")) : null;

    Page<Place> page;
    if (hasRegion) {
      if (hasCategory && hasSubCategory) {
        page = placeRepository.findByCategoryInAndPlaceCategory_SubCategoryAndAddressContaining(
            categories, subCategory, region, pageable);
      } else if (hasCategory) {
        page = placeRepository.findByCategoryInAndAddressContaining(categories, region, pageable);
      } else {
        page = placeRepository.findByAddressContaining(region, pageable);
      }
    } else if (hasCategory && hasSubCategory) {
      page = placeRepository.findByCategoryAndPlaceCategory_SubCategory(category, subCategory, pageable);
    } else if (!hasCategory) {
      page = placeRepository.findAll(pageable);
    } else if (hasCategory && categories.size() > 1) {
      page = placeRepository.findByCategoryIn(categories, pageable);
    } else {
      page = placeRepository.findByCategory(category, pageable);
    }

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
      @RequestParam(defaultValue = "300") int limit) {
    List<Place> places;
    if (category == null || category.isBlank()) {
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
      @RequestParam(defaultValue = "30") int limit) {
    int pool = Math.max(limit * 20, 500);
    List<Place> candidates = placeRepository.findNearestPlaces(lat, lon, pool);

    List<String> categories = (category == null || category.isBlank())
        ? null
        : List.of(category.split(","));

    List<Place> filtered = candidates.stream()
        .filter(place -> categories == null || categories.contains(place.getCategory()))
        .filter(place -> subCategory == null || subCategory.isBlank()
            || (place.getPlaceCategory() != null && subCategory.equals(place.getPlaceCategory().getSubCategory())))
        .filter(place -> region == null || region.isBlank()
            || (place.getAddress() != null && place.getAddress().contains(region)))
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
  public ResponseEntity<Map<String, Long>> getCategoryCounts(@RequestParam(required = false) String region) {
    boolean hasRegion = region != null && !region.isBlank();
    List<Object[]> rows = hasRegion
        ? placeRepository.countGroupedByCategoryAndAddressContaining(region)
        : placeRepository.countGroupedByCategory();

    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    for (Object[] row : rows) {
      counts.put((String) row[0], (Long) row[1]);
    }
    return ResponseEntity.ok(counts);
  }

  // 숙박 탭처럼 대분류 하나를 세부분류 칩으로 한 번 더 쪼개서 보여줄 때 쓰는 개수 집계 API.
  @GetMapping("/category-counts/sub")
  public ResponseEntity<Map<String, Long>> getSubCategoryCounts(
      @RequestParam String category, @RequestParam(required = false) String region) {
    boolean hasRegion = region != null && !region.isBlank();
    List<Object[]> rows = hasRegion
        ? placeRepository.countGroupedBySubCategoryAndAddressContaining(category, region)
        : placeRepository.countGroupedBySubCategory(category);

    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    for (Object[] row : rows) {
      counts.put((String) row[0], (Long) row[1]);
    }
    return ResponseEntity.ok(counts);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getPlace(@PathVariable Long id) {
    Optional<Place> placeOpt = placeRepository.findById(id);
    if (placeOpt.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "장소를 찾을 수 없습니다"));
    }
    PlaceStyle style = placeStyleRepository.findByPlace_Id(id).orElse(null);
    return ResponseEntity.ok(PlaceResponseDto.from(placeOpt.get(), style));
  }
}
