package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.init.PlaceCategoryKeywords;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 이름 키워드로도, 네이버 지역 검색으로도 못 잡은 미분류 장소(주로 리뷰가 거의 없는 초소형 맛집,
 * 2026-08-27 실측 - 남은 4.4만건 중 98%가 맛집·관광지·숙박 같은 평범한 소형 업체)를 소상공인시장
 * 진흥공단 상가업소정보 API(국세청/카드사 데이터 기반, 전국 소상공인 커버)로 재분류한다.
 * 네이버와 달리 이 API는 "반경 내 전체 업체 목록"만 주고 이름검색을 지원하지 않으므로, 우리 장소
 * 좌표를 중심으로 반경검색한 뒤 이름이 일치하는 후보만 신뢰한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SbizCategoryMatchServiceImpl implements SbizCategoryMatchService {

  private static final String LIST_URL = "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius";
  private static final int RADIUS_METERS = 150;
  private static final int CONSECUTIVE_ERROR_LIMIT = 20;

  private final PlaceRepository placeRepository;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final PlaceClosureCheckService placeClosureCheckService;
  private final RestTemplate restTemplate = buildRestTemplate();

  private static RestTemplate buildRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }

  @Value("${sbiz.service-key}")
  private String serviceKey;

  private record Candidate(String name, String midCategory, String subCategory, String ksicName,
                            Double lat, Double lon) {
  }

  @Override
  public MatchResult matchUnclassifiedPlaces(int limit) {
    List<Place> targets = placeRepository.findByPlaceCategoryIsNull().stream().limit(limit).toList();

    int attempted = 0;
    int matched = 0;
    int apiNoResult = 0;
    int noConfidentCandidate = 0;
    int noKeywordMatch = 0;
    int apiError = 0;
    int consecutiveApiErrors = 0;
    boolean stoppedEarly = false;
    int closureSuspected = 0;

    for (Place place : targets) {
      if (place.getLatitude() == null || place.getLongitude() == null
          || place.getName() == null || place.getName().isBlank()) {
        continue;
      }
      attempted++;

      String rawCategory = place.getCategory();
      String parent = PlaceCategoryKeywords.PERFORMANCE_GENRES.contains(rawCategory)
          ? "공연"
          : PlaceCategoryKeywords.PARENT_ALIASES.getOrDefault(rawCategory, rawCategory);

      List<Candidate> candidates;
      try {
        candidates = search(place.getLongitude(), place.getLatitude());
        consecutiveApiErrors = 0;
      } catch (Exception e) {
        log.warn("소상공인 반경검색 실패 (placeId={}, name={})", place.getId(), place.getName(), e);
        apiError++;
        consecutiveApiErrors++;
        sleepBriefly();
        if (consecutiveApiErrors >= CONSECUTIVE_ERROR_LIMIT) {
          log.warn("소상공인 API 연속 {}회 호출 실패 - 배치를 중단합니다 (시도 {}건째)", consecutiveApiErrors, attempted);
          stoppedEarly = true;
          break;
        }
        continue;
      }
      if (candidates.isEmpty()) {
        apiNoResult++;
        if (placeClosureCheckService.flagIfKakaoAlsoMisses(place)) closureSuspected++;
        sleepBriefly();
        continue;
      }

      Candidate best = pickByName(place.getName(), candidates);
      if (best == null) {
        noConfidentCandidate++;
        if (placeClosureCheckService.flagIfKakaoAlsoMisses(place)) closureSuspected++;
        sleepBriefly();
        continue;
      }

      String matchedText = best.midCategory() + " " + best.subCategory() + " " + best.ksicName();
      String matchedSub = PlaceCategoryKeywords.findSubCategory(parent, matchedText);
      if (matchedSub == null) {
        noKeywordMatch++;
        sleepBriefly();
        continue;
      }

      PlaceCategory category = placeCategoryRepository
          .findByParentCategoryAndSubCategory(parent, matchedSub)
          .orElse(null);
      if (category == null) {
        noKeywordMatch++;
        sleepBriefly();
        continue;
      }

      place.setPlaceCategory(category);
      placeRepository.save(place);
      matched++;
      sleepBriefly();
    }

    log.info("소상공인 카테고리 매칭 완료 - 시도 {}건 중 연결 {}건 (결과없음 {}, 후보불확실 {}, 키워드매칭실패 {}, API오류 {}, 조기중단 {}, 폐업추정 {})",
        attempted, matched, apiNoResult, noConfidentCandidate, noKeywordMatch, apiError, stoppedEarly, closureSuspected);
    return new MatchResult(attempted, matched, apiNoResult, noConfidentCandidate, noKeywordMatch, apiError, stoppedEarly, closureSuspected);
  }

  // 반경 내 후보 중 우리 장소 이름과 일치(포함 관계)하는 것만 신뢰한다 - 좌표만으로는 옆 가게를
  // 잘못 연결할 위험이 있어서, 이름 일치를 1차 조건으로 삼는다.
  private Candidate pickByName(String placeName, List<Candidate> candidates) {
    String normalizedTarget = normalize(placeName);
    for (Candidate c : candidates) {
      String normalizedCandidate = normalize(c.name());
      if (normalizedCandidate.isEmpty()) continue;
      if (normalizedTarget.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedTarget)) {
        return c;
      }
    }
    return null;
  }

  // 초당 요청 제한(429, "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR")에 걸려서
  // (2026-08-27 실측) 요청 사이에 딜레이를 둔다. 다른 data.go.kr 연동(TourAPI 등)보다 제한이
  // 빡빡해 보여서 조금 더 여유 있게 250ms로 둔다.
  private void sleepBriefly() {
    try {
      Thread.sleep(250);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private String normalize(String text) {
    if (text == null) return "";
    return text.replaceAll("[\\s()\\[\\]-]", "");
  }

  private List<Candidate> search(double lon, double lat) {
    // serviceKey의 +,/,=를 URLEncoder로 직접 인코딩해야 한다(TourApiSyncServiceImpl과 동일한 이유 -
    // UriComponentsBuilder.encode()는 +를 인코딩 안 해서 서버가 공백으로 오해석함).
    String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
    String url = UriComponentsBuilder.fromUriString(LIST_URL)
        .queryParam("serviceKey", encodedServiceKey)
        .queryParam("cx", lon)
        .queryParam("cy", lat)
        .queryParam("radius", RADIUS_METERS)
        .queryParam("type", "json")
        .build(true)
        .toUri()
        .toString();

    List<Candidate> result = new ArrayList<>();
    JsonNode root = restTemplate.getForObject(URI.create(url), JsonNode.class);
    if (root == null) return result;

    JsonNode header = root.path("header");
    String resultCode = header.path("resultCode").asText("");
    if (!"00".equals(resultCode)) {
      // NODATA_ERROR는 진짜 오류가 아니라 "이 반경 안엔 등록된 업체가 없음"이라는 정상 응답이다
      // (2026-08-27 실측 - 리조트/콘도처럼 소상공인 DB에 안 잡히는 대형 시설에서 흔함). apiError로
      // 잘못 집계하면 폐업 추정 교차검증(카카오)도 안 타게 되므로 여기서 빈 결과로 취급해야 한다.
      if ("NODATA_ERROR".equals(header.path("resultMsg").asText(""))) {
        return result;
      }
      throw new IllegalStateException("소상공인 API 오류: " + header.path("resultMsg").asText(""));
    }

    for (JsonNode item : root.path("body").path("items")) {
      result.add(new Candidate(
          item.path("bizesNm").asText(""),
          item.path("indsMclsNm").asText(""),
          item.path("indsSclsNm").asText(""),
          item.path("ksicNm").asText(""),
          item.path("lat").isNumber() ? item.path("lat").asDouble() : null,
          item.path("lon").isNumber() ? item.path("lon").asDouble() : null
      ));
    }
    return result;
  }
}
