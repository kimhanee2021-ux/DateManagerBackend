package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.service.CultureEventSyncService;
import org.ict.datemanagerbackend.domain.place.service.KakaoPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.LodgingCsvSyncService;
import org.ict.datemanagerbackend.domain.place.service.MuseumSyncService;
import org.ict.datemanagerbackend.domain.place.service.NaverPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.PlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.TourApiSyncService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

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

  public AdminPlaceServiceImpl(PlaceRepository placeRepository, PlaceCategoryRepository placeCategoryRepository,
                                PlaceSyncService placeSyncService, TourApiSyncService tourApiSyncService,
                                MuseumSyncService museumSyncService, NaverPlaceSyncService naverPlaceSyncService,
                                KakaoPlaceSyncService kakaoPlaceSyncService, LodgingCsvSyncService lodgingCsvSyncService,
                                CultureEventSyncService cultureEventSyncService) {
    this.placeRepository = placeRepository;
    this.placeCategoryRepository = placeCategoryRepository;
    this.placeSyncService = placeSyncService;
    this.tourApiSyncService = tourApiSyncService;
    this.museumSyncService = museumSyncService;
    this.naverPlaceSyncService = naverPlaceSyncService;
    this.kakaoPlaceSyncService = kakaoPlaceSyncService;
    this.lodgingCsvSyncService = lodgingCsvSyncService;
    this.cultureEventSyncService = cultureEventSyncService;
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
      default -> throw new IllegalArgumentException(
          "source는 kopis, festival, tourapi, museum, naver, kakao, cultureinfo, lodging, boxoffice 중 하나여야 합니다");
    }
  }

  @Override
  public Map<String, Integer> cleanupBlacklistedPlaces() {
    return tourApiSyncService.cleanupBlacklistedPlaces();
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
    csv.append("id,parentCategory,subCategory,emoji,scoreEnergy,scoreImmersion,scoreVibe,scoreAesthetic,scoreDepth,isIndoor,isActivity\n");
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
          .append(pc.getIsIndoor()).append(',')
          .append(pc.getIsActivity()).append('\n');
    }
    return csv.toString();
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
