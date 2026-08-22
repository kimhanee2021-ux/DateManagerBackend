package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * TourAPI(공공데이터포털) 출처 장소는 원본 mapx/mapy 자체가 실제 위치와 수백m씩 어긋나는 사례가
 * 있다(2026-08-20, "홍원" - 신촌 인근에서 실측하다 발견. TourAPI 원본을 재조회해도 DB와 같은 값이라
 * 우리 동기화 파싱 문제가 아니라 한국관광공사 원본 데이터 오류로 확인됨). 주소를 카카오 로컬 API로
 * 재지오코딩해서 크게 어긋난 좌표만 교정한다.
 *
 * <p>검증이 끝난 장소는 Place.coordinateVerified=true로 표시해두고, TourApiSyncServiceImpl의 매일
 * 새벽 재동기화가 이 플래그를 보고 lat/lng를 TourAPI 원본 값으로 다시 덮어쓰지 않도록 건너뛴다 -
 * 안 그러면 여기서 고친 좌표가 다음 날 새벽에 다시 틀어진다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceCoordinateVerificationServiceImpl implements PlaceCoordinateVerificationService {

  private static final String ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";
  private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
  private static final String EXTERNAL_SOURCE = "TOURAPI";
  private static final int BATCH_SIZE = 200;
  private static final int MAX_BATCHES = 500; // 안전장치 - 최대 10만 건까지
  // 기존 좌표와 재지오코딩 결과가 이 거리(m) 이상 벌어지면 원본 오류로 보고 교정한다. 서로 다른
  // 두 지오코더가 같은 곳을 가리켜도 도로명주소 정밀도 차이로 수십m는 흔히 벌어지므로, 그보다
  // 넉넉하게 잡아 오탐(정상 좌표를 잘못 덮어쓰는 것)을 피한다.
  private static final double CORRECTION_THRESHOLD_METERS = 200.0;
  private static final double EARTH_RADIUS_METERS = 6371000.0;
  private static final long THROTTLE_MILLIS = 120; // 카카오 로컬 API 초당 호출 제한 대비

  private final PlaceRepository placeRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${kakao.rest-api-key}")
  private String kakaoRestApiKey;

  @Override
  public void verifyTourApiCoordinates() {
    int corrected = 0;
    int alreadyAccurate = 0;
    int noGeocodeResult = 0;
    int processed = 0;

    for (int batch = 0; batch < MAX_BATCHES; batch++) {
      // 검증이 끝난 행은 조건에서 빠지므로 매번 0페이지를 다시 읽으면 된다(진행할수록 대상이 줄어듦).
      Page<Place> page = placeRepository.findUnverifiedTourApiPlaces(EXTERNAL_SOURCE, PageRequest.of(0, BATCH_SIZE));
      if (page.isEmpty()) break;

      for (Place place : page) {
        GeocodeResult geo = geocodeAddress(place.getAddress());
        if (geo == null) {
          geo = geocodeKeyword(place.getName(), place.getLatitude(), place.getLongitude());
        }

        if (geo == null) {
          noGeocodeResult++;
        } else if (place.getLatitude() == null || place.getLongitude() == null
            || haversineMeters(place.getLatitude(), place.getLongitude(), geo.lat(), geo.lng()) > CORRECTION_THRESHOLD_METERS) {
          place.setLatitude(geo.lat());
          place.setLongitude(geo.lng());
          corrected++;
        } else {
          alreadyAccurate++;
        }

        place.setCoordinateVerified(true);
        placeRepository.save(place);
        processed++;
      }

      log.info("TourAPI 좌표 재검증 진행 중 - 누적 {}건 처리 (교정 {}, 정확 {}, 결과없음 {})",
          processed, corrected, alreadyAccurate, noGeocodeResult);
    }

    log.info("TourAPI 좌표 재검증 완료 - 총 {}건 처리, 교정 {}건, 이미 정확 {}건, 재지오코딩 실패 {}건",
        processed, corrected, alreadyAccurate, noGeocodeResult);
  }

  private record GeocodeResult(Double lat, Double lng) {
  }

  private GeocodeResult geocodeAddress(String address) {
    if (address == null || address.isBlank()) return null;
    sleep();
    String url = UriComponentsBuilder.fromUriString(ADDRESS_SEARCH_URL)
        .queryParam("query", address)
        .queryParam("size", 1)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();
    try {
      JsonNode first = callKakao(url).path("documents").get(0);
      return first == null ? null : toResult(first);
    } catch (Exception e) {
      log.warn("주소 재지오코딩 실패 (address={})", address, e);
      return null;
    }
  }

  // 주소 검색이 실패했을 때만 이름으로 재시도한다 - 기존(어긋났을 수도 있는) 좌표를 중심으로 반경
  // 5km 안에서만 찾아서, 전혀 다른 지역의 동명 장소가 잘못 매칭되는 걸 최대한 막는다.
  private GeocodeResult geocodeKeyword(String name, Double biasLat, Double biasLng) {
    if (name == null || name.isBlank()) return null;
    sleep();
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(KEYWORD_SEARCH_URL)
        .queryParam("query", name)
        .queryParam("size", 1);
    if (biasLat != null && biasLng != null) {
      builder.queryParam("x", biasLng).queryParam("y", biasLat).queryParam("radius", 5000);
    }
    String url = builder.encode(StandardCharsets.UTF_8).build().toUriString();
    try {
      JsonNode first = callKakao(url).path("documents").get(0);
      return first == null ? null : toResult(first);
    } catch (Exception e) {
      log.warn("키워드 재지오코딩 실패 (name={})", name, e);
      return null;
    }
  }

  private JsonNode callKakao(String url) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "KakaoAK " + kakaoRestApiKey);
    return restTemplate.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(null, headers), JsonNode.class)
        .getBody();
  }

  private GeocodeResult toResult(JsonNode document) {
    Double lng = parseCoordinate(document.path("x").asText(""));
    Double lat = parseCoordinate(document.path("y").asText(""));
    if (lat == null || lng == null) return null;
    return new GeocodeResult(lat, lng);
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void sleep() {
    try {
      Thread.sleep(THROTTLE_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // CourseServiceImpl.haversineMeters와 같은 공식.
  private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }
}
