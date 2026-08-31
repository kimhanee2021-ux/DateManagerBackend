package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceClosureCheckServiceImpl implements PlaceClosureCheckService {

  private static final String KAKAO_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

  private final PlaceRepository placeRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  // KakaoPlaceSyncServiceImpl과 같은 키(kakao.rest-api-key) 재사용.
  @Value("${kakao.rest-api-key}")
  private String kakaoRestApiKey;

  // 삭제는 하지 않는다 - Place.closureSuspected 주석 참고, 사람이 검토 후 삭제할 "의심 목록"일 뿐이다.
  @Override
  public boolean flagIfKakaoAlsoMisses(Place place) {
    if (Boolean.TRUE.equals(place.getClosureSuspected())) return false; // 이미 표시된 장소는 다시 안 셈
    try {
      if (kakaoHasNameMatch(place.getName())) {
        return false;
      }
    } catch (Exception e) {
      log.warn("카카오 교차검증 실패 (placeId={}, name={}) - 이번엔 폐업 추정 보류", place.getId(), place.getName(), e);
      return false;
    }
    place.setClosureSuspected(true);
    placeRepository.save(place);
    return true;
  }

  private boolean kakaoHasNameMatch(String placeName) {
    String url = UriComponentsBuilder.fromUriString(KAKAO_SEARCH_URL)
        .queryParam("query", placeName)
        .queryParam("size", 15)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "KakaoAK " + kakaoRestApiKey);

    JsonNode root = restTemplate.exchange(
        URI.create(url), HttpMethod.GET, new HttpEntity<>(null, headers), JsonNode.class
    ).getBody();
    if (root == null) return false;

    String normalizedTarget = normalize(placeName);
    for (JsonNode doc : root.path("documents")) {
      String normalizedCandidate = normalize(doc.path("place_name").asText(""));
      if (normalizedCandidate.isEmpty()) continue;
      if (normalizedTarget.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedTarget)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String text) {
    if (text == null) return "";
    return text.replaceAll("[\\s()\\[\\]-]", "");
  }
}
