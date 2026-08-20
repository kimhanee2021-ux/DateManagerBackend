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

// 장소 동기화는 원래 매일 새벽에 자동(@Scheduled)으로만 도는데, 개발 중 수동으로 바로 실행해서
// 결과를 확인하고 싶을 때 쓰는 관리자 전용 트리거를 모아둔 서비스.
@Service
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

  public AdminPlaceServiceImpl(PlaceRepository placeRepository, PlaceCategoryRepository placeCategoryRepository,
                                PlaceSyncService placeSyncService, TourApiSyncService tourApiSyncService,
                                MuseumSyncService museumSyncService, NaverPlaceSyncService naverPlaceSyncService,
                                KakaoPlaceSyncService kakaoPlaceSyncService, LodgingCsvSyncService lodgingCsvSyncService,
                                CultureEventSyncService cultureEventSyncService, PlaceDedupService placeDedupService,
                                PlaceStyleRepository placeStyleRepository, PlaceRealityRepository placeRealityRepository,
                                PlaceAmenityRepository placeAmenityRepository, SportsSyncService sportsSyncService,
                                PlaceCoordinateVerificationService placeCoordinateVerificationService) {
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
    this.placeCoordinateVerificationService = placeCoordinateVerificationService;
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
}
