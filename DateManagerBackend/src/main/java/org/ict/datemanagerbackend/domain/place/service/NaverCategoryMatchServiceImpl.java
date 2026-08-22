package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.init.PlaceCategoryKeywords;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 이름 키워드만으로는 세부분류를 못 잡는 장소(전체 79,495건 중 약 5만 건, 2026-08-22 실측)를
 * 네이버 지역 검색 API의 실제 category 태그(예: "숙박&gt;펜션")로 다시 분류해본다. 이름 추측보다
 * 훨씬 신뢰도 높은 원본 분류 정보라, 같은 키워드 테이블(PlaceCategoryKeywords)을 그대로 재사용해도
 * 적중률이 크게 오를 것으로 기대. 다만 "이름만으로 검색"하면 동명이인 업체가 걸릴 위험이 있어서
 * (실측: "화천파크" 검색 1위가 전혀 다른 골프연습장이었음) 반드시 우리가 이미 갖고 있는 좌표와
 * 가까운(PlaceDedupServiceImpl과 동일 반경) 후보만 신뢰한다 - 안 그러면 엉뚱한 성향점수가 들어간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverCategoryMatchServiceImpl implements NaverCategoryMatchService {

  private static final String LIST_URL = "https://naverapihub.apigw.ntruss.com/search/v1/local";
  private static final double COORDINATE_SCALE = 1e7;
  // PlaceDedupServiceImpl과 동일 기준(약 200~280m) - 소스마다 지오코딩 좌표가 조금씩 달라서
  // 너무 좁히면(50m 등) 진짜 같은 곳도 놓친다.
  private static final double RADIUS_DEGREES = 0.0025;

  private final PlaceRepository placeRepository;
  private final PlaceCategoryRepository placeCategoryRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${naver.search.client-id}")
  private String clientId;

  @Value("${naver.search.client-secret}")
  private String clientSecret;

  private record Candidate(String title, String category, Double lat, Double lng) {
  }

  @Override
  public MatchResult matchUnclassifiedPlaces(int limit) {
    List<Place> targets = placeRepository.findByPlaceCategoryIsNull().stream().limit(limit).toList();

    int attempted = 0;
    int matched = 0;
    int apiNoResult = 0;
    int noConfidentCandidate = 0;
    int noKeywordMatch = 0;

    for (Place place : targets) {
      if (place.getLatitude() == null || place.getLongitude() == null
          || place.getName() == null || place.getName().isBlank()) {
        continue; // 좌표/이름 없으면 진짜 후보인지 검증할 방법이 없어서 건너뜀(오분류 방지)
      }
      attempted++;

      String rawCategory = place.getCategory();
      String parent = PlaceCategoryKeywords.PERFORMANCE_GENRES.contains(rawCategory)
          ? "공연"
          : PlaceCategoryKeywords.PARENT_ALIASES.getOrDefault(rawCategory, rawCategory);

      List<Candidate> candidates;
      try {
        candidates = search(regionPrefix(place.getAddress()) + " " + place.getName());
      } catch (Exception e) {
        log.warn("네이버 지역 검색 실패 (placeId={}, name={})", place.getId(), place.getName(), e);
        candidates = List.of();
      }
      if (candidates.isEmpty()) {
        apiNoResult++;
        sleepBriefly();
        continue;
      }

      Candidate best = pickConfidentCandidate(place, candidates);
      if (best == null) {
        noConfidentCandidate++;
        sleepBriefly();
        continue;
      }

      String matchedSub = PlaceCategoryKeywords.findSubCategory(parent, best.category());
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

    log.info("네이버 카테고리 매칭 완료 - 시도 {}건 중 연결 {}건 (API결과없음 {}, 후보불확실 {}, 키워드매칭실패 {})",
        attempted, matched, apiNoResult, noConfidentCandidate, noKeywordMatch);
    return new MatchResult(attempted, matched, apiNoResult, noConfidentCandidate, noKeywordMatch);
  }

  // 후보 중 우리 장소와 좌표가 가장 가까운 것을 고르되, RADIUS_DEGREES를 벗어나면 아예 후보로
  // 인정하지 않는다(동명이인 업체를 잘못 연결하는 것보다 미분류로 남기는 게 안전).
  private Candidate pickConfidentCandidate(Place place, List<Candidate> candidates) {
    Candidate best = null;
    double bestDistSq = Double.MAX_VALUE;
    for (Candidate c : candidates) {
      if (c.lat() == null || c.lng() == null) continue;
      double dLat = c.lat() - place.getLatitude();
      double dLng = c.lng() - place.getLongitude();
      if (Math.abs(dLat) > RADIUS_DEGREES || Math.abs(dLng) > RADIUS_DEGREES) continue;
      double distSq = dLat * dLat + dLng * dLng;
      if (distSq < bestDistSq) {
        bestDistSq = distSq;
        best = c;
      }
    }
    return best;
  }

  // "서울특별시 종로구 봉익동 ..." -> "서울특별시 종로구"만 잘라서 검색어 앞에 붙인다(동명이인 방지).
  private String regionPrefix(String address) {
    if (address == null || address.isBlank()) return "";
    String[] tokens = address.trim().split("\\s+");
    if (tokens.length >= 2) return tokens[0] + " " + tokens[1];
    return tokens[0];
  }

  private List<Candidate> search(String query) {
    String url = UriComponentsBuilder.fromUriString(LIST_URL)
        .queryParam("query", query)
        .queryParam("display", 5)
        .queryParam("start", 1)
        .queryParam("sort", "comment")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    List<Candidate> result = new ArrayList<>();

    // NaverPlaceSyncServiceImpl과 동일한 이유 - 응답 Content-Type이 text/plain이라 자동 컨버터로는
    // 한글이 깨져서 바이트로 받아 직접 UTF-8 디코딩한다.
    byte[] rawBody = restTemplate.exchange(
        URI.create(url), HttpMethod.GET, new HttpEntity<>(null, authHeaders()), byte[].class
    ).getBody();
    if (rawBody == null || rawBody.length == 0) return result;

    JsonNode root = JsonMapper.builder().build().readTree(new String(rawBody, StandardCharsets.UTF_8));
    for (JsonNode item : root.path("items")) {
      result.add(new Candidate(
          stripHtml(item.path("title").asText("")),
          item.path("category").asText(""),
          parseCoordinate(item.path("mapy").asText(null)),
          parseCoordinate(item.path("mapx").asText(null))
      ));
    }
    return result;
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-NCP-APIGW-API-KEY-ID", clientId);
    headers.add("X-NCP-APIGW-API-KEY", clientSecret);
    return headers;
  }

  private String stripHtml(String value) {
    return value.replaceAll("</?b>", "").trim();
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value) / COORDINATE_SCALE;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void sleepBriefly() {
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
