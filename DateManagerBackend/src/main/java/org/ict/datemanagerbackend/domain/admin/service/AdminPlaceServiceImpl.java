package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.place.dto.PlaceDumpDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.place.repository.PlaceAmenityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRealityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.ict.datemanagerbackend.domain.place.service.CultureEventSyncService;
import org.ict.datemanagerbackend.domain.place.service.KakaoPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.LodgingCsvSyncService;
import org.ict.datemanagerbackend.domain.place.service.MuseumSyncService;
import org.ict.datemanagerbackend.domain.place.service.NaverCategoryMatchService;
import org.ict.datemanagerbackend.domain.place.service.SbizCategoryMatchService;
import org.ict.datemanagerbackend.domain.place.service.TourApiDetailSyncService;
import org.ict.datemanagerbackend.domain.place.service.NaverPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.PlaceCoordinateVerificationService;
import org.ict.datemanagerbackend.domain.place.service.PlaceDedupService;
import org.ict.datemanagerbackend.domain.place.service.PlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.SportsSyncService;
import org.ict.datemanagerbackend.domain.place.service.TourApiSyncService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// 장소 동기화는 원래 매일 새벽에 자동(@Scheduled)으로만 도는데, 개발 중 수동으로 바로 실행해서
// 결과를 확인하고 싶을 때 쓰는 관리자 전용 트리거를 모아둔 서비스.
@Service
@Slf4j
public class AdminPlaceServiceImpl implements AdminPlaceService {

  private final PlaceRepository placeRepository;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final PlaceSyncService placeSyncService;
  private final TourApiSyncService tourApiSyncService;
  private final MuseumSyncService museumSyncService;
  private final NaverPlaceSyncService naverPlaceSyncService;
  private final KakaoPlaceSyncService kakaoPlaceSyncService;
  private final LodgingCsvSyncService lodgingCsvSyncService;
  private final CultureEventSyncService cultureEventSyncService;
  private final PlaceDedupService placeDedupService;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceRealityRepository placeRealityRepository;
  private final PlaceAmenityRepository placeAmenityRepository;
  private final SportsSyncService sportsSyncService;
  private final PlaceCoordinateVerificationService placeCoordinateVerificationService;
  private final NaverCategoryMatchService naverCategoryMatchService;
  private final TourApiDetailSyncService tourApiDetailSyncService;
  private final SbizCategoryMatchService sbizCategoryMatchService;

  public AdminPlaceServiceImpl(PlaceRepository placeRepository, PlaceCategoryRepository placeCategoryRepository,
                                PlaceSyncService placeSyncService, TourApiSyncService tourApiSyncService,
                                MuseumSyncService museumSyncService, NaverPlaceSyncService naverPlaceSyncService,
                                KakaoPlaceSyncService kakaoPlaceSyncService, LodgingCsvSyncService lodgingCsvSyncService,
                                CultureEventSyncService cultureEventSyncService, PlaceDedupService placeDedupService,
                                PlaceStyleRepository placeStyleRepository, PlaceRealityRepository placeRealityRepository,
                                PlaceAmenityRepository placeAmenityRepository, SportsSyncService sportsSyncService,
                                PlaceCoordinateVerificationService placeCoordinateVerificationService,
                                NaverCategoryMatchService naverCategoryMatchService,
                                TourApiDetailSyncService tourApiDetailSyncService,
                                SbizCategoryMatchService sbizCategoryMatchService) {
    this.placeRepository = placeRepository;
    this.placeCategoryRepository = placeCategoryRepository;
    this.placeSyncService = placeSyncService;
    this.tourApiSyncService = tourApiSyncService;
    this.museumSyncService = museumSyncService;
    this.naverPlaceSyncService = naverPlaceSyncService;
    this.kakaoPlaceSyncService = kakaoPlaceSyncService;
    this.lodgingCsvSyncService = lodgingCsvSyncService;
    this.cultureEventSyncService = cultureEventSyncService;
    this.placeDedupService = placeDedupService;
    this.placeStyleRepository = placeStyleRepository;
    this.placeRealityRepository = placeRealityRepository;
    this.placeAmenityRepository = placeAmenityRepository;
    this.sportsSyncService = sportsSyncService;
    this.naverCategoryMatchService = naverCategoryMatchService;
    this.placeCoordinateVerificationService = placeCoordinateVerificationService;
    this.tourApiDetailSyncService = tourApiDetailSyncService;
    this.sbizCategoryMatchService = sbizCategoryMatchService;
  }

  @Override
  public void syncSource(String source) {
    switch (source) {
      case "kopis" -> placeSyncService.syncPerformances();
      case "festival" -> placeSyncService.syncFestivals();
      case "tourapi" -> tourApiSyncService.syncPlaces();
      case "museum" -> museumSyncService.syncMuseums();
      case "naver" -> naverPlaceSyncService.syncPlaces();
      case "kakao" -> kakaoPlaceSyncService.syncPlaces();
      case "cultureinfo" -> cultureEventSyncService.syncEvents();
      case "lodging" -> lodgingCsvSyncService.syncFromCsv();
      case "boxoffice" -> placeSyncService.syncBoxOffice();
      case "sports" -> sportsSyncService.syncPlaces();
      default -> throw new IllegalArgumentException(
          "source는 kopis, festival, tourapi, museum, naver, kakao, cultureinfo, lodging, boxoffice, sports 중 하나여야 합니다");
    }
  }

  @Override
  public Map<String, Integer> cleanupBlacklistedPlaces() {
    return tourApiSyncService.cleanupBlacklistedPlaces();
  }

  @Override
  public Map<String, Integer> mergeDuplicatePlaces() {
    return placeDedupService.mergeDuplicatePlaces();
  }

  @Override
  public Map<String, Integer> backfillLodgingImagesFromTourApi() {
    return placeDedupService.backfillLodgingImagesFromTourApi();
  }

  // TourAPI 3만8천여건을 카카오와 하나씩 대조하느라 수십 분~1시간대까지 걸릴 수 있어(2026-08-20),
  // 다른 관리자 트리거처럼 동기로 기다리게 하면 HTTP 요청이 타임아웃난다. 가상 스레드로 띄우고
  // 바로 응답만 반환 - 진행 상황/완료 여부는 서버 로그(PlaceCoordinateVerificationServiceImpl)로 확인.
  @Override
  public Map<String, String> verifyTourApiCoordinates() {
    Thread.ofVirtual().name("tourapi-coord-verify").start(placeCoordinateVerificationService::verifyTourApiCoordinates);
    return Map.of(
        "status", "started",
        "message", "백그라운드로 실행을 시작했습니다. 진행 상황과 완료 여부는 서버 로그에서 확인하세요."
    );
  }

  @Override
  public Map<String, Long> getSyncStatus() {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (Object[] row : placeRepository.countGroupedByCategory()) {
      counts.put((String) row[0], (Long) row[1]);
    }
    counts.put("전체", placeRepository.count());
    return counts;
  }

  // 빅데이터 분석/외부 업데이트를 위한 전체 장소 백업(CSV). 8만여 건이라 엔티티로 로드하지 않고
  // PlaceRepository.findAllForExport()의 프로젝션 쿼리 한 방으로 필요한 컬럼만 가져온다(2026-08-14).
  @Override
  public String exportPlacesCsv() {
    StringBuilder csv = new StringBuilder();
    csv.append("id,name,category,subCategory,emoji,address,latitude,longitude,externalSource,")
        .append("scoreEnergy,scoreImmersion,scoreVibe,scoreAesthetic,scoreDepth\n");
    for (Object[] row : placeRepository.findAllForExport()) {
      csv.append(row[0]).append(',')
          .append(csvEscape((String) row[1])).append(',')
          .append(csvEscape((String) row[2])).append(',')
          .append(csvEscape((String) row[3])).append(',')
          .append(csvEscape((String) row[4])).append(',')
          .append(csvEscape((String) row[5])).append(',')
          .append(row[6] == null ? "" : row[6]).append(',')
          .append(row[7] == null ? "" : row[7]).append(',')
          .append(csvEscape((String) row[8])).append(',')
          .append(row[9] == null ? "" : row[9]).append(',')
          .append(row[10] == null ? "" : row[10]).append(',')
          .append(row[11] == null ? "" : row[11]).append(',')
          .append(row[12] == null ? "" : row[12]).append(',')
          .append(row[13] == null ? "" : row[13]).append('\n');
    }
    return csv.toString();
  }

  // place_categories(9개 대분류 x 세부분류 성향점수 공식) 전체 백업(CSV).
  @Override
  public String exportPlaceCategoriesCsv() {
    StringBuilder csv = new StringBuilder();
    csv.append("id,parentCategory,subCategory,emoji,scoreEnergy,scoreImmersion,scoreVibe,scoreAesthetic,scoreDepth,scorePacing,isIndoor,isActivity\n");
    for (PlaceCategory pc : placeCategoryRepository.findAll()) {
      csv.append(pc.getId()).append(',')
          .append(csvEscape(pc.getParentCategory())).append(',')
          .append(csvEscape(pc.getSubCategory())).append(',')
          .append(csvEscape(pc.getEmoji())).append(',')
          .append(pc.getScoreEnergy()).append(',')
          .append(pc.getScoreImmersion()).append(',')
          .append(pc.getScoreVibe()).append(',')
          .append(pc.getScoreAesthetic()).append(',')
          .append(pc.getScoreDepth()).append(',')
          .append(pc.getScorePacing()).append(',')
          .append(pc.getIsIndoor()).append(',')
          .append(pc.getIsActivity()).append('\n');
    }
    return csv.toString();
  }

  // 팀원 공유용 place 전체 백업(2026-08-20). exportPlacesCsv와 달리 place_categories와 join하지 않고
  // places 테이블 원본 컬럼 그대로 + 1:1/1:N 자식 테이블(style/reality/amenity)을 내려준다 - 이걸
  // importFullDump에 그대로 넣으면 받는 사람 DB에 place_styles/place_realities/place_amenities까지
  // 재현된다. place_category_id/performance_rankings를 뺀 이유는 PlaceDumpDto 주석 참고.
  @Override
  public List<PlaceDumpDto> exportFullDump() {
    List<Place> places = placeRepository.findAll();
    Map<Long, PlaceStyle> styleByPlaceId = placeStyleRepository.findAll().stream()
        .collect(Collectors.toMap(PlaceStyle::getPlaceId, s -> s));
    Map<Long, PlaceReality> realityByPlaceId = placeRealityRepository.findAll().stream()
        .collect(Collectors.toMap(PlaceReality::getPlaceId, r -> r));
    Map<Long, List<String>> amenitiesByPlaceId = placeAmenityRepository.findAll().stream()
        .collect(Collectors.groupingBy(a -> a.getPlace().getId(),
            Collectors.mapping(PlaceAmenity::getAmenityTag, Collectors.toList())));

    List<PlaceDumpDto> dump = new ArrayList<>(places.size());
    for (Place place : places) {
      PlaceStyle style = styleByPlaceId.get(place.getId());
      PlaceReality reality = realityByPlaceId.get(place.getId());
      PlaceDumpDto.StyleDto styleDto = style == null ? null : new PlaceDumpDto.StyleDto(
          style.getScoreEnergy(), style.getScoreImmersion(), style.getScoreVibe(),
          style.getScoreAesthetic(), style.getScoreDepth(), style.getIsIndoor(), style.getIsActivity());
      PlaceDumpDto.RealityDto realityDto = reality == null ? null : new PlaceDumpDto.RealityDto(
          reality.getWaitingStatus(), reality.getWaitingTeams(), reality.getReservationType(),
          reality.getPriceText(), reality.getParkingInfo());

      dump.add(new PlaceDumpDto(
          place.getName(), place.getCategory(), place.getAddress(), place.getLatitude(), place.getLongitude(),
          place.getImageUrl(), place.getExternalSource(), place.getExternalId(),
          place.getStartDate(), place.getEndDate(), place.getRuntimeText(), place.getPriceInfo(),
          place.getShowTimeInfo(), place.getBookingUrl(), place.getIsOpenRun(), place.getPerformanceState(),
          styleDto, realityDto, amenitiesByPlaceId.getOrDefault(place.getId(), List.of())
      ));
    }
    return dump;
  }

  // exportFullDump로 받은 목록을 내 DB에 반영한다. 각 항목을 external_source+external_id로 먼저
  // 찾고(있으면 그게 가장 정확한 매칭), 없으면 이름+좌표 기준 dedup으로 한 번 더 찾는다 - 둘 다
  // 실패해야 신규 생성. id를 그대로 옮기지 않고 항상 내 DB의 자동증가 id를 새로 받기 때문에, 보내는
  // 사람과 받는 사람의 place_id가 달라도 문제없다(place_style/reality/amenity는 이 메서드 안에서
  // 새로 받은 id에 맞춰 연결한다).
  @Override
  public Map<String, Integer> importFullDump(List<PlaceDumpDto> dump) {
    int created = 0;
    int updated = 0;

    for (PlaceDumpDto dto : dump) {
      if (dto.name() == null || dto.name().isBlank()) {
        continue;
      }

      Optional<Place> existing = (dto.externalSource() != null && dto.externalId() != null)
          ? placeRepository.findByExternalSourceAndExternalId(dto.externalSource(), dto.externalId())
          : Optional.empty();
      if (existing.isEmpty()) {
        existing = placeDedupService.findDuplicate(dto.name(), dto.latitude(), dto.longitude());
      }

      Place place;
      if (existing.isPresent()) {
        place = existing.get();
        place.setName(dto.name());
        place.setCategory(dto.category());
        place.setAddress(dto.address());
        place.setLatitude(dto.latitude());
        place.setLongitude(dto.longitude());
        place.setImageUrl(dto.imageUrl());
        place.setStartDate(dto.startDate());
        place.setEndDate(dto.endDate());
        place.setRuntimeText(dto.runtimeText());
        place.setPriceInfo(dto.priceInfo());
        place.setShowTimeInfo(dto.showTimeInfo());
        place.setBookingUrl(dto.bookingUrl());
        place.setIsOpenRun(dto.isOpenRun());
        place.setPerformanceState(dto.performanceState());
        placeRepository.save(place);
        updated++;
      } else {
        place = placeRepository.save(Place.builder()
            .name(dto.name())
            .category(dto.category())
            .address(dto.address())
            .latitude(dto.latitude())
            .longitude(dto.longitude())
            .imageUrl(dto.imageUrl())
            .externalSource(dto.externalSource())
            .externalId(dto.externalId())
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .runtimeText(dto.runtimeText())
            .priceInfo(dto.priceInfo())
            .showTimeInfo(dto.showTimeInfo())
            .bookingUrl(dto.bookingUrl())
            .isOpenRun(dto.isOpenRun())
            .performanceState(dto.performanceState())
            .build());
        created++;
      }

      if (dto.style() != null) {
        PlaceDumpDto.StyleDto s = dto.style();
        PlaceStyle style = placeStyleRepository.findByPlace_Id(place.getId())
            .orElseGet(() -> PlaceStyle.builder().place(place).build());
        style.setScoreEnergy(s.scoreEnergy());
        style.setScoreImmersion(s.scoreImmersion());
        style.setScoreVibe(s.scoreVibe());
        style.setScoreAesthetic(s.scoreAesthetic());
        style.setScoreDepth(s.scoreDepth());
        style.setIsIndoor(s.isIndoor());
        style.setIsActivity(s.isActivity());
        placeStyleRepository.save(style);
      }

      if (dto.reality() != null) {
        PlaceDumpDto.RealityDto r = dto.reality();
        PlaceReality reality = placeRealityRepository.findByPlace_Id(place.getId())
            .orElseGet(() -> PlaceReality.builder().place(place).build());
        reality.setWaitingStatus(r.waitingStatus());
        reality.setWaitingTeams(r.waitingTeams());
        reality.setReservationType(r.reservationType());
        reality.setPriceText(r.priceText());
        reality.setParkingInfo(r.parkingInfo());
        placeRealityRepository.save(reality);
      }

      if (dto.amenities() != null && !dto.amenities().isEmpty()) {
        List<PlaceAmenity> existingAmenities = placeAmenityRepository.findByPlace_IdIn(List.of(place.getId()));
        java.util.Set<String> existingTags = existingAmenities.stream()
            .map(PlaceAmenity::getAmenityTag).collect(Collectors.toSet());
        for (String tag : dto.amenities()) {
          if (!existingTags.contains(tag)) {
            placeAmenityRepository.save(PlaceAmenity.builder().place(place).amenityTag(tag).build());
          }
        }
      }
    }

    return Map.of("created", created, "updated", updated);
  }

  // CSV 필드에 콤마/따옴표/줄바꿈이 섞여 있어도(장소명·주소에 흔함) 깨지지 않게 감싸는 이스케이프.
  private String csvEscape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  // place_categories CSV import(2026-08-20) - exportPlaceCategoriesCsv와 완전히 같은 컬럼 순서
  // (id는 무시). PlaceCategorySeeder와 동일하게 parentCategory+subCategory로 매칭해 upsert한다 -
  // 팀원이 코드 pull/재시작 없이도 CSV 파일만으로 최신 성향점수를 바로 반영할 수 있게 하려는 용도.
  // 첫 줄(헤더)은 건너뛴다.
  @Override
  public Map<String, Integer> importPlaceCategoriesCsv(String csv) {
    int created = 0;
    int updated = 0;
    int skipped = 0;
    List<String> lines = csv.lines().toList();
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) continue;
      List<String> cols = parseCsvLine(line);
      if (cols.size() < 12) {
        skipped++;
        continue;
      }

      String parentCategory = cols.get(1);
      String subCategory = cols.get(2);
      String emoji = cols.get(3);
      Integer energy = parseIntOrNull(cols.get(4));
      Integer immersion = parseIntOrNull(cols.get(5));
      Integer vibe = parseIntOrNull(cols.get(6));
      Integer aesthetic = parseIntOrNull(cols.get(7));
      Integer depth = parseIntOrNull(cols.get(8));
      Integer pacing = parseIntOrNull(cols.get(9));
      Integer isIndoor = parseIntOrNull(cols.get(10));
      Integer isActivity = parseIntOrNull(cols.get(11));

      PlaceCategory category = placeCategoryRepository
          .findByParentCategoryAndSubCategory(parentCategory, subCategory)
          .orElse(null);

      if (category == null) {
        placeCategoryRepository.save(
            PlaceCategory.builder()
                .parentCategory(parentCategory)
                .subCategory(subCategory)
                .emoji(emoji)
                .scoreEnergy(energy)
                .scoreImmersion(immersion)
                .scoreVibe(vibe)
                .scoreAesthetic(aesthetic)
                .scoreDepth(depth)
                .scorePacing(pacing)
                .isIndoor(isIndoor)
                .isActivity(isActivity)
                .build()
        );
        created++;
        continue;
      }

      category.setEmoji(emoji);
      category.setScoreEnergy(energy);
      category.setScoreImmersion(immersion);
      category.setScoreVibe(vibe);
      category.setScoreAesthetic(aesthetic);
      category.setScoreDepth(depth);
      category.setScorePacing(pacing);
      category.setIsIndoor(isIndoor);
      category.setIsActivity(isActivity);
      placeCategoryRepository.save(category);
      updated++;
    }
    return Map.of("created", created, "updated", updated, "skipped", skipped);
  }

  // TourAPI 상세정보(운영시간/휴무일/입장료/주차/편의시설/사진갤러리) 동기화(2026-08-26).
  // 장소 하나당 API를 2번(intro+image)씩 불러서 시간이 걸릴 수 있어, 네이버 매칭 배치가 curl
  // 타임아웃으로 끊겼던 것과 같은 문제를 피하려고 verifyTourApiCoordinates와 동일하게 가상 스레드로
  // 띄우고 바로 응답만 반환한다 - 진행 상황/완료 여부는 서버 로그로 확인.
  @Override
  public Map<String, String> syncTourApiDetails(int limit) {
    // 수동 트리거 경로엔 완료 로그가 없어서, 백그라운드 스레드가 끝났는지 서버 로그로 확인할
    // 방법이 없었다(2026-09-02 발견) - scheduledSync()와 동일한 형식으로 완료 로그를 남긴다.
    Thread.ofVirtual().name("tourapi-detail-sync").start(() -> {
      var result = tourApiDetailSyncService.syncDetails(limit);
      log.info("TourAPI 상세정보 수동 동기화 완료 - 시도 {}건, 채움 {}건, 정보없음 {}건, 실패 {}건",
          result.attempted(), result.filled(), result.noData(), result.failed());
    });
    return Map.of(
        "status", "started",
        "message", "백그라운드로 실행을 시작했습니다. 진행 상황과 완료 여부는 서버 로그에서 확인하세요."
    );
  }

  // 네이버 지역 검색 API의 실제 category 태그로 이름 키워드만으론 못 잡던 장소를 재분류(2026-08-22).
  // NaverCategoryMatchService.MatchResult -> Map으로 펼쳐서 다른 관리자 API와 반환 타입을 통일한다.
  @Override
  public Map<String, Object> matchPlaceCategoriesViaNaver(int limit) {
    var result = naverCategoryMatchService.matchUnclassifiedPlaces(limit);
    return Map.of(
        "attempted", result.attempted(),
        "matched", result.matched(),
        "apiNoResult", result.apiNoResult(),
        "noConfidentCandidate", result.noConfidentCandidate(),
        "noKeywordMatch", result.noKeywordMatch(),
        "apiError", result.apiError(),
        "stoppedEarly", result.stoppedEarly()
    );
  }

  // 네이버로도 못 잡은 미분류 장소를 소상공인시장진흥공단 상가정보 API로 재분류(2026-08-27).
  @Override
  public Map<String, Object> matchPlaceCategoriesViaSbiz(int limit) {
    var result = sbizCategoryMatchService.matchUnclassifiedPlaces(limit);
    return Map.of(
        "attempted", result.attempted(),
        "matched", result.matched(),
        "apiNoResult", result.apiNoResult(),
        "noConfidentCandidate", result.noConfidentCandidate(),
        "noKeywordMatch", result.noKeywordMatch(),
        "apiError", result.apiError(),
        "stoppedEarly", result.stoppedEarly(),
        "closureSuspected", result.closureSuspected()
    );
  }

  // 장소↔세부분류 연결(place_category_id) CSV 백업(2026-08-22). PlaceDumpDto와 똑같은 이유로 raw id
  // 대신 (externalSource, externalId) + parentCategory/subCategory 문자열로 내보낸다 - 받는 사람 DB의
  // place_categories 자동증가 id가 나와 다를 수 있어서, id를 그대로 옮기면 엉뚱한 분류에 연결될 위험이
  // 있다. import 쪽에서 parentCategory+subCategory로 다시 조회해 안전하게 연결한다.
  @Override
  public String exportPlaceCategoryLinksCsv() {
    StringBuilder csv = new StringBuilder();
    csv.append("externalSource,externalId,parentCategory,subCategory\n");
    for (Place place : placeRepository.findAll()) {
      if (place.getPlaceCategory() == null || place.getExternalSource() == null || place.getExternalId() == null) {
        continue;
      }
      csv.append(csvEscape(place.getExternalSource())).append(',')
          .append(csvEscape(place.getExternalId())).append(',')
          .append(csvEscape(place.getPlaceCategory().getParentCategory())).append(',')
          .append(csvEscape(place.getPlaceCategory().getSubCategory())).append('\n');
    }
    return csv.toString();
  }

  // exportPlaceCategoryLinksCsv로 받은 CSV를 그대로 올리면, 이 서버가 자기 로컬 DB의 place_categories에서
  // parentCategory+subCategory로 다시 찾아 연결한다 - 매칭 로직(네이버 API 호출 등)을 팀원 컴퓨터마다
  // 다시 돌릴 필요 없이 결과만 그대로 반영된다. 첫 줄(헤더)은 건너뛴다.
  @Override
  public Map<String, Integer> importPlaceCategoryLinksCsv(String csv) {
    int linked = 0;
    int placeNotFound = 0;
    int categoryNotFound = 0;
    List<String> lines = csv.lines().toList();
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) continue;
      List<String> cols = parseCsvLine(line);
      if (cols.size() < 4) continue;

      String externalSource = cols.get(0);
      String externalId = cols.get(1);
      String parentCategory = cols.get(2);
      String subCategory = cols.get(3);

      Place place = placeRepository.findByExternalSourceAndExternalId(externalSource, externalId).orElse(null);
      if (place == null) {
        placeNotFound++;
        continue;
      }
      PlaceCategory category = placeCategoryRepository
          .findByParentCategoryAndSubCategory(parentCategory, subCategory)
          .orElse(null);
      if (category == null) {
        categoryNotFound++;
        continue;
      }
      place.setPlaceCategory(category);
      placeRepository.save(place);
      linked++;
    }
    return Map.of("linked", linked, "placeNotFound", placeNotFound, "categoryNotFound", categoryNotFound);
  }

  // 콤마/따옴표가 포함된 필드를 감안한 최소 CSV 파서(csvEscape의 역연산) - 외부 라이브러리 없이
  // exportPlaceCategoriesCsv 포맷 전용으로만 쓴다.
  private List<String> parseCsvLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        result.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    result.add(cur.toString());
    return result;
  }

  private Integer parseIntOrNull(String s) {
    if (s == null || s.isBlank()) return null;
    return Integer.parseInt(s.trim());
  }
}
