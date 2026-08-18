package org.ict.datemanagerbackend.domain.place.service;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.TourApiPlaceDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한국관광공사 TourAPI(국문 관광정보서비스, KorService2)의 지역기반 목록조회(areaBasedList2)에서
 * 장소 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * PlaceSyncService(KOPIS 공연 동기화)와 동일하게 external_source+external_id로 upsert한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TourApiSyncService {

  private static final String LIST_URL = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2";

  // 우리 DB에서 "이 데이터가 TourAPI에서 왔다"는 걸 표시하기 위한 값 (Place.externalSource에 저장됨)
  private static final String EXTERNAL_SOURCE = "TOURAPI";

  // TourAPI 콘텐츠타입ID -> 우리 서비스 카테고리명 (contenttypeid는 목록 API에서 그대로 내려주지 않는 경우가
  // 있어, 호출할 때 지정한 contentTypeId를 그대로 카테고리로 사용한다)
  private static final Map<String, String> CATEGORY_BY_CONTENT_TYPE = Map.of(
      "12", "관광지",
      "14", "문화시설",
      "15", "공연",
      "28", "액티비티",
      "32", "숙박", // CSV로 임포트한 숙박(문화_숙박업.csv)엔 사진이 없어서, 사진 있는 숙박 데이터를 이걸로 보강
      "38", "쇼핑",
      "39", "맛집"
  );

  // 전국 어디서나 지점이 반복되는 프랜차이즈/체인점은 "데이트 장소"로서 변별력이 없어 목록을 도배하므로
  // 이름에 이 키워드가 포함되면 동기화 자체를 건너뛴다(특히 쇼핑 카테고리에 올리브영 등이 지점마다
  // 중복 노출되던 문제, 2026-08-14).
  // "약국"은 TourAPI가 쇼핑(38) 카테고리로 잘못 분류해서 들어오는 경우(2026-08-18 확인, 227건)
  // - 데이트 장소가 아니라서 프랜차이즈 편의점/올리브영과 같은 이유로 제외한다.
  private static final List<String> BLACKLISTED_NAME_KEYWORDS = List.of(
      "올리브영", "다이소", "이마트24", "GS25", "CU", "세븐일레븐", "미니스톱", "약국"
  );

  private boolean isBlacklisted(String name) {
    return name != null && BLACKLISTED_NAME_KEYWORDS.stream().anyMatch(name::contains);
  }

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceDedupService placeDedupService;
  private final RestTemplate restTemplate = new RestTemplate();

  // application.yaml의 tourapi.service-key 값을 그대로 주입받음 (yaml -> .env의 TOUR_API_SERVICE_KEY로 연결됨)
  @Value("${tourapi.service-key}")
  private String serviceKey;

  /**
   * 매일 새벽 4시에 자동 실행됨 (KOPIS 동기화가 새벽 3시라 시간을 겹치지 않게 뒀다).
   */
  @Scheduled(cron = "0 0 4 * * *")
  public void syncPlaces() {
    int created = 0;
    int updated = 0;

    for (Map.Entry<String, String> entry : CATEGORY_BY_CONTENT_TYPE.entrySet()) {
      String contentTypeId = entry.getKey();
      String category = entry.getValue();
      List<TourApiPlaceDto> places = fetchPlaces(contentTypeId);

      for (TourApiPlaceDto p : places) {
        if (isBlacklisted(p.title())) {
          continue;
        }

        Optional<Place> existing =
            placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, p.contentid());

        Double lat = parseCoordinate(p.mapy());
        Double lng = parseCoordinate(p.mapx());
        String image = !p.firstimage().isBlank() ? p.firstimage() : p.firstimage2();
        String address = p.addr2().isBlank() ? p.addr1() : (p.addr1() + " " + p.addr2()).trim();

        if (existing.isPresent()) {
          Place place = existing.get();
          place.setName(p.title());
          place.setCategory(category);
          place.setAddress(address);
          place.setLatitude(lat);
          place.setLongitude(lng);
          place.setImageUrl(image);
          placeRepository.save(place);
          updated++;
          continue;
        }

        // external_source+external_id로는 못 걸러낸다 - 같은 실제 장소가 다른 소스(KOPIS/네이버/카카오
        // 등)에서 이미 저장돼 있을 수 있으므로 이름+좌표로 한 번 더 확인한다.
        Optional<Place> duplicate = placeDedupService.findDuplicate(p.title(), lat, lng);
        if (duplicate.isPresent()) {
          Place place = duplicate.get();
          if (place.getImageUrl() == null || place.getImageUrl().isBlank()) {
            place.setImageUrl(image); // 이미지 없던 기존 장소면 이번 소스의 이미지로 채워줌
            placeRepository.save(place);
          }
          updated++;
        } else {
          Place place = Place.builder()
              .name(p.title())
              .category(category)
              .address(address)
              .latitude(lat)
              .longitude(lng)
              .imageUrl(image)
              .externalSource(EXTERNAL_SOURCE)
              .externalId(p.contentid())
              .build();
          placeRepository.save(place);
          created++;
        }
      }
    }

    log.info("TourAPI 장소 동기화 완료 - 신규 {}건, 갱신 {}건", created, updated);

    // TODO: 전화번호, 가격/주차 정보는 이 목록 API(areaBasedList2)에는 안 들어있음.
    //       필요하면 contentid로 상세조회 API(detailIntro2)를 한 번 더 호출해서
    //       PlaceReality.priceText/parkingInfo를 채우는 로직을 추가할 것.
  }

  // 블랙리스트 필터 도입(2026-08-14) 이전에 이미 저장되어 있던 프랜차이즈/체인점을 정리한다.
  // 관리자가 수동으로 한 번 호출하는 일회성 정리용 메서드(AdminController에서 호출).
  // place_styles/course_items 등 자식 테이블이 이 장소를 참조하고 있으면 FK 제약(ORA-02292)으로
  // 삭제가 실패하는데, 그런 장소는 이미 코스에 담기는 등 실사용 데이터가 있다는 뜻이라 억지로 지우지
  // 않고 건너뛴다. 한 건씩 개별 트랜잭션으로 처리해서, 하나가 막혀도 나머지 삭제는 계속 진행된다.
  public Map<String, Integer> cleanupBlacklistedPlaces() {
    int deleted = 0;
    int skipped = 0;
    for (String keyword : BLACKLISTED_NAME_KEYWORDS) {
      for (Place place : placeRepository.findByNameContaining(keyword)) {
        try {
          // place_styles는 매 장소마다 1:1로 자동 생성돼 있어서(성향점수 중립값), 이건 실사용
          // 데이터가 아니라 부산물이므로 먼저 지워도 안전하다.
          placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
          placeRepository.delete(place);
          placeRepository.flush();
          deleted++;
        } catch (DataIntegrityViolationException e) {
          skipped++;
        }
      }
    }
    log.info("블랙리스트 장소 정리 완료 - {}건 삭제, {}건 연관 데이터로 건너뜀", deleted, skipped);
    return Map.of("deleted", deleted, "skipped", skipped);
  }

  // TourAPI가 한 번에 내려주는 최대 건수(그 이상 요청하면 페이지가 잘림)
  private static final int TOURAPI_PAGE_SIZE = 100;

  // 무한 루프 방지용 안전장치. 카테고리당 최대 100 * 200 = 20000건까지 수집.
  // (실측 결과 맛집 13,515건/관광지 12,644건/쇼핑 12,242건이 가장 많아 여유 있게 잡음 - 200페이지면 충분)
  private static final int TOURAPI_MAX_PAGES = 200;

  /**
   * contentTypeId 하나에 대해 전국 목록을 전부 받아옴. areaBasedList2는 한 번에 최대 100건만
   * 내려주기 때문에(numOfRows 상한), pageNo를 1씩 늘려가며 totalCount만큼 다 모을 때까지 반복 호출한다.
   * (예전엔 pageNo=1 고정이라 카테고리당 최대 100건만 저장되고 나머지는 전부 유실되던 버그가 있었음 -
   *  예: 맛집은 전국 13,515건인데 100건만 저장되고 있었음)
   */
  private List<TourApiPlaceDto> fetchPlaces(String contentTypeId) {
    List<TourApiPlaceDto> all = new ArrayList<>();

    for (int page = 1; page <= TOURAPI_MAX_PAGES; page++) {
      // serviceKey의 +,/,=를 URLEncoder로 직접 인코딩(UriComponentsBuilder.encode()는 +를 인코딩 안 해서
      // 공공데이터포털 서버가 공백으로 오해석하는 문제가 있었음).
      String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
      String url = UriComponentsBuilder.fromUriString(LIST_URL)
          .queryParam("serviceKey", encodedServiceKey)
          .queryParam("MobileOS", "ETC")
          .queryParam("MobileApp", "DateManager")
          .queryParam("_type", "json")
          .queryParam("numOfRows", TOURAPI_PAGE_SIZE)
          .queryParam("pageNo", page)
          .queryParam("contentTypeId", contentTypeId)
          .build(true) // true: 위 값들을 이미 인코딩된 것으로 취급 (serviceKey 이중 인코딩 방지)
          .toUriString();

      List<TourApiPlaceDto> pageItems;
      try {
        // 주의: getForObject(String, ...)는 문자열을 URI 템플릿으로 보고 내부적으로 다시 한 번
        // 인코딩한다 - 이미 인코딩해둔 serviceKey가 %2F -> %252F 처럼 이중 인코딩되어 완전히 다른
        // 값이 전송되는 버그가 있었다("등록되지 않은 서비스키" 에러의 진짜 원인). URI로 직접 넘기면
        // RestTemplate이 재인코딩 없이 그대로 보낸다.
        JsonNode root = restTemplate.getForObject(java.net.URI.create(url), JsonNode.class);
        if (root == null) break;

        JsonNode items = root.path("response").path("body").path("items").path("item");
        pageItems = new ArrayList<>();
        for (JsonNode item : items) {
          String contentId = item.path("contentid").asText("");
          String title = item.path("title").asText("");
          if (contentId.isBlank() || title.isBlank()) continue; // 필수 정보 없는 항목은 건너뜀

          pageItems.add(new TourApiPlaceDto(
              contentId,
              title,
              item.path("addr1").asText(""),
              item.path("addr2").asText(""),
              item.path("mapx").asText(""),
              item.path("mapy").asText(""),
              item.path("firstimage").asText(""),
              item.path("firstimage2").asText("")
          ));
        }
      } catch (Exception e) {
        log.error("TourAPI 호출 실패 (contentTypeId={}, page={})", contentTypeId, page, e);
        break;
      }

      if (pageItems.isEmpty()) break;
      all.addAll(pageItems);

      if (pageItems.size() < TOURAPI_PAGE_SIZE) break; // 마지막 페이지까지 다 읽음
    }

    return all;
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
