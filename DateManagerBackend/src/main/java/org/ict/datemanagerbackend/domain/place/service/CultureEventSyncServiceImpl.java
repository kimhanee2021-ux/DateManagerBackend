package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.CultureEventDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한국문화정보원 문화정보조회서비스(cultureinfo API, 기간별 목록조회 /period2)에서 전시/축제/체험
 * 정보를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * 이 API는 TourAPI/공공데이터포털 표준데이터군과 같은 계정(TOUR_API_SERVICE_KEY)으로 바로 쓸 수
 * 있고(활용신청만 별도), 응답에 gpsX/gpsY 좌표가 직접 포함돼 있어 KOPIS처럼 시설 상세조회를 따로
 * 안 해도 된다. 분야(realmName)가 다양한데, 그중 우리 카테고리와 맞아떨어지고 아직 데이터가
 * 부족했던 것만 골라서 담는다(연극/뮤지컬 등 공연 장르는 KOPIS가 이미 충분히 커버하고 있어 제외).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CultureEventSyncServiceImpl implements CultureEventSyncService {

  private static final String LIST_URL = "https://apis.data.go.kr/B553457/cultureinfo/period2";
  private static final String EXTERNAL_SOURCE = "CULTUREINFO";

  // realmName(분야) -> 우리 서비스 카테고리. 목록에 없는 분야(연극/뮤지컬,오페라/음악,콘서트 등)는
  // KOPIS가 이미 담당하고 있어 건너뛴다.
  private static final Map<String, String> CATEGORY_BY_REALM = Map.of(
      "전시", "문화시설",
      "행사/축제", "축제",
      "교육/체험", "액티비티"
  );

  private static final int PAGE_SIZE = 100;
  // 무한 루프 방지용 안전장치. 페이지당 100건 * 50페이지 = 최대 5000건까지 수집.
  private static final int MAX_PAGES = 50;

  private final PlaceRepository placeRepository;
  private final PlaceDedupService placeDedupService;
  private final RestTemplate restTemplate = new RestTemplate();

  // application.yaml의 tourapi.service-key를 그대로 공유해서 쓴다 (TourApiSyncService/MuseumSyncService와 동일한 이유).
  @Value("${tourapi.service-key}")
  private String serviceKey;

  // 좌표->실제 도로명주소 역지오코딩용(2026-08-27, 사용자 요청) - KakaoPlaceSyncServiceImpl과 같은 키.
  @Value("${kakao.rest-api-key}")
  private String kakaoRestApiKey;

  /** 매일 새벽 4시 45분에 자동 실행됨 (박물관 4시30분 다음으로 배치). */
  @Scheduled(cron = "0 45 4 * * *")
  @Override
  public void syncEvents() {
    LocalDate today = LocalDate.now();
    // 전시는 몇 달씩 이어지는 경우가 많아 넉넉하게 180일 범위로 조회한다.
    List<CultureEventDto> events = fetchAll(today, today.plusDays(180));

    int created = 0;
    int updated = 0;
    int skipped = 0;

    for (CultureEventDto e : events) {
      String category = CATEGORY_BY_REALM.get(e.realmName());
      if (category == null || e.title() == null || e.title().isBlank()
          || e.gpsX() == null || e.gpsY() == null) {
        skipped++;
        continue;
      }

      Double lng = parseCoordinate(e.gpsX());
      Double lat = parseCoordinate(e.gpsY());
      // e.place()는 시설명("국립현대미술관 서울관")일 뿐 실제 주소가 아니다(2026-08-27 사용자 지적 -
      // "주소를 불러올 때 해당 장소가 나와야"). 좌표를 카카오로 역지오코딩해 실제 도로명주소를 얻고,
      // 실패하면(카카오 API 오류 등) 시설명으로 폴백한다.
      String address = reverseGeocodeAddress(lat, lng);
      if (address == null) address = e.place();

      Optional<Place> existing = placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, e.seq());
      if (existing.isPresent()) {
        Place place = existing.get();
        place.setName(e.title());
        place.setCategory(category);
        place.setAddress(address);
        place.setLatitude(lat);
        place.setLongitude(lng);
        // "국립현대미술관 서울관" 같은 장소명을 venueName에도 저장한다(2026-08-27) - "같은 미술관의
        // 다른 전시" 그룹핑용(공연/공연장과 같은 방향). 이 API는 KOPIS의 mt10id 같은 깔끔한 시설
        // ID가 없어서 venueId는 비워두고, venue-performances 조회 쪽에서 venueName 일치로 대신
        // 그룹핑한다.
        place.setVenueName(e.place());
        if (e.thumbnail() != null && !e.thumbnail().isBlank()) {
          place.setImageUrl(e.thumbnail());
        }
        placeRepository.save(place);
        updated++;
        continue;
      }

      // TourAPI/KOPIS/박물관 등 다른 소스에 이미 있는 같은 실제 장소인지 확인 (특히 "전시"는 기존
      // 박물관/미술관 데이터와 겹칠 가능성이 높음)
      Optional<Place> duplicate = placeDedupService.findDuplicate(e.title(), lat, lng);
      if (duplicate.isPresent()) {
        updated++;
        continue;
      }

      Place place = Place.builder()
          .name(e.title())
          .category(category)
          .address(address)
          .latitude(lat)
          .longitude(lng)
          .imageUrl(e.thumbnail())
          .externalSource(EXTERNAL_SOURCE)
          .externalId(e.seq())
          .venueName(e.place())
          .build();
      placeRepository.save(place);
      created++;
    }

    log.info("문화정보(전시/축제/체험) 동기화 완료 - 신규 {}건, 갱신 {}건, 대상 아님 {}건 (전체 조회 {}건)",
        created, updated, skipped, events.size());
  }

  private List<CultureEventDto> fetchAll(LocalDate start, LocalDate end) {
    String from = start.format(DateTimeFormatter.BASIC_ISO_DATE);
    String to = end.format(DateTimeFormatter.BASIC_ISO_DATE);

    List<CultureEventDto> all = new ArrayList<>();

    for (int page = 1; page <= MAX_PAGES; page++) {
      String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
      String url = UriComponentsBuilder.fromUriString(LIST_URL)
          .queryParam("serviceKey", encodedServiceKey)
          .queryParam("from", from)
          .queryParam("to", to)
          .queryParam("pageNo", page)
          .queryParam("numOfrows", PAGE_SIZE) // 대소문자까지 정확히 이 이름이어야 반영됨 (실측 확인 - numOfRows/rows는 무시되고 기본값 10건만 내려옴)
          .build(true)
          .toUriString();

      List<CultureEventDto> pageItems;
      try {
        // 이 API도 KOPIS와 마찬가지로 Content-Type이 charset 없는 "application/xml"이라 String으로
        // 바로 받으면 한글이 깨질 수 있어(실측 확인), 바이트로 받아 XML 파서가 스스로 인코딩을
        // 판단하게 한다 (PlaceSyncService와 동일한 이유/패턴).
        byte[] xmlBytes = restTemplate.getForObject(java.net.URI.create(url), byte[].class);
        pageItems = parseEvents(xmlBytes);
      } catch (Exception ex) {
        log.error("문화정보 API 호출 실패 (page={})", page, ex);
        break;
      }

      if (pageItems.isEmpty()) break;
      all.addAll(pageItems);

      if (pageItems.size() < PAGE_SIZE) break; // 마지막 페이지까지 다 읽음
    }

    return all;
  }

  private List<CultureEventDto> parseEvents(byte[] xmlBytes) {
    List<CultureEventDto> result = new ArrayList<>();
    if (xmlBytes == null || xmlBytes.length == 0) return result;

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new java.io.ByteArrayInputStream(xmlBytes)));

      NodeList items = doc.getElementsByTagName("item");
      for (int i = 0; i < items.getLength(); i++) {
        Element el = (Element) items.item(i);
        result.add(new CultureEventDto(
            text(el, "seq"),
            text(el, "title"),
            text(el, "place"),
            text(el, "realmName"),
            text(el, "gpsX"),
            text(el, "gpsY"),
            text(el, "thumbnail")
        ));
      }
    } catch (Exception e) {
      log.error("문화정보 API 응답 파싱 실패", e);
    }

    return result;
  }

  private String text(Element parent, String tag) {
    NodeList nl = parent.getElementsByTagName(tag);
    if (nl.getLength() == 0) return null;
    String value = nl.item(0).getTextContent();
    return (value == null || value.isBlank()) ? null : value;
  }

  // 좌표 -> 실제 도로명주소 역지오코딩(2026-08-27). PlaceController.reverseGeocodeRegion(coord2regioncode,
  // "시/도"만 줌)보다 더 구체적인 coord2address로 도로명/지번 주소까지 받는다. 도로명주소가 있으면
  // 그걸, 없으면(신축 건물 등) 지번주소로 대체한다. 실패하면 null - 호출부가 시설명으로 폴백한다.
  private String reverseGeocodeAddress(Double lat, Double lon) {
    if (lat == null || lon == null) return null;
    String url = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/geo/coord2address.json")
        .queryParam("x", lon)
        .queryParam("y", lat)
        .toUriString();
    try {
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.add("Authorization", "KakaoAK " + kakaoRestApiKey);
      tools.jackson.databind.JsonNode root = restTemplate.exchange(
          java.net.URI.create(url), org.springframework.http.HttpMethod.GET,
          new org.springframework.http.HttpEntity<>(null, headers), tools.jackson.databind.JsonNode.class
      ).getBody();
      if (root == null) return null;
      tools.jackson.databind.JsonNode first = root.path("documents").get(0);
      if (first == null) return null;
      String roadAddress = first.path("road_address").path("address_name").asText("");
      if (!roadAddress.isBlank()) return roadAddress;
      String jibunAddress = first.path("address").path("address_name").asText("");
      return jibunAddress.isBlank() ? null : jibunAddress;
    } catch (Exception e) {
      log.warn("좌표 역지오코딩 실패 (lat={}, lon={})", lat, lon, e);
      return null;
    }
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
