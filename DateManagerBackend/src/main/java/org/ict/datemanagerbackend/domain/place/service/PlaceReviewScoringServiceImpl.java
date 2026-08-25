package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.PlaceReviewResultDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 리뷰 기반 장소별 점수 보정(2026-08-25 착수) 구현체. AiChatServiceImpl과 동일하게 OpenAI Responses
 * API를 RestTemplate로 직접 호출하는 패턴을 따르되, 여기서는 대화(previous_response_id)가 아니라
 * 장소 하나당 단발성 web_search 호출 하나로 끝난다.
 *
 * <p>중요: web_search가 준 imageUrl은 모델이 검색 스니펫을 보고 "그럴듯하게" 종합한 값이라 실제로
 * 존재하지 않거나 깨진 링크일 수 있다(파일럿 테스트 중 확인) - 그래서 DB에 그대로 저장하지 않고
 * HTTP HEAD로 실제 이미지가 맞는지 검증한 것만 Place.imageUrl에 반영한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewScoringServiceImpl implements PlaceReviewScoringService {

  private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
  private static final String MODEL = "gpt-4o-mini";

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${openai.api-key}")
  private String apiKey;

  // 6축 정의 - AiChatServiceImpl의 AXIS_KOREAN_NAME과 동일한 축 이름 체계를 쓴다.
  private static final String AXIS_GUIDE = """
      - energy(에너지): 활동적이고 역동적인 곳일수록 높게(예: 야외 액티비티, 시끌벅적한 술집), \
      차분하고 여유로운 곳일수록 낮게(예: 조용한 북카페).
      - immersion(몰입): 오래 머무르며 몰입하는 곳일수록 높게(예: 전시, 방탈출), 잠깐 들르고 마는 \
      곳일수록 낮게.
      - vibe(바이브): 감성적이고 트렌디한 분위기·인테리어일수록 높게, 평범하고 기능 위주면 낮게.
      - aesthetic(미적): 사진 찍을 거리·볼거리가 풍부할수록 높게, 볼거리가 적으면 낮게.
      - depth(깊이): 지적 깊이나 전문성·학습 요소가 클수록 높게(예: 박물관, 클래식 공연), 가볍게 \
      즐기는 곳이면 낮게.
      - pacing(즉흥·계획): 예약·웨이팅이 필요하거나 미리 계획해야 하는 곳일수록 높게, 즉흥 방문이 \
      쉬우면 낮게.
      """;

  // "정말 못 찾았을 때만 포기해"처럼 도피구를 프롬프트에 명시할수록 모델이 검색을 1번만 해보고
  // 바로 포기하는 경향이 파일럿 테스트 중 반복 확인됐다(2026-08-25) - 프롬프트 지시만으로는
  // 검색 재시도를 강제할 수 없어서, 1차 결과가 부실하면 아래 requestReviewAnalysis가 코드에서
  // 직접 2차 검색 요청을 previous_response_id로 이어 보낸다.
  private static final String PROMPT_TEMPLATE = """
      아래 장소에 대한 실제 방문객 리뷰를 네이버 블로그, 네이버지도/카카오맵 리뷰, 구글 리뷰 등에서
      찾아봐.

      장소명: %s
      주소: %s
      대분류: %s / 세부분류: %s
      %s

      리뷰를 찾았으면, 그 내용을 바탕으로 이 장소가 같은 세부분류의 "평균적인" 장소보다 아래 6개
      축에서 어느 쪽으로 치우치는지 0~100 점수로 매겨줘(50=평균, 위 세부분류 기준값이 있으면 그
      값에서 이 장소의 특성만큼 가감). 축 기준:
      %s

      대략적인 평점(5점 만점 추정)과 리뷰 개수 느낌, 대표 사진이 있다면 그 이미지의 실제 URL도
      알려줘. 못 찾은 값은 null로 둬.

      반드시 아래 JSON 형식으로만 답해(다른 설명 텍스트 없이, 마크다운 코드블록도 쓰지 마):
      {"scoreEnergy": number|null, "scoreImmersion": number|null, "scoreVibe": number|null, \
      "scoreAesthetic": number|null, "scoreDepth": number|null, "scorePacing": number|null, \
      "rating": number|null, "reviewCount": number|null, "imageUrl": string|null, "summary": string}
      """;

  private static final String RETRY_PROMPT = """
      방금 검색으로는 리뷰를 충분히 못 찾은 것 같아. 이번엔 완전히 다른 검색어로 다시 찾아봐 -
      장소명만으로 안 됐으면 "동네이름 + 장소명", "장소명 인스타", "장소명 티스토리"처럼 바꿔서
      시도해봐. 그래도 정말 못 찾았으면 이번엔 그냥 모든 값을 null로 답해도 돼. 같은 JSON 형식으로만
      답해(다른 설명 텍스트 없이, 마크다운 코드블록도 쓰지 마).
      """;

  @Override
  public List<PlaceReviewResultDto> runReviewPilot(List<Long> placeIds) {
    List<PlaceReviewResultDto> results = new ArrayList<>();
    for (Long placeId : placeIds) {
      results.add(runOne(placeId));
    }
    return results;
  }

  // 결과가 사실상 "아무것도 못 찾음"인지 판단 - rating/reviewCount/imageUrl이 전부 비어있으면
  // 6축 점수만 있어도(모델이 그냥 중립값 50을 채워 넣은 경우가 많음) 신뢰할 근거가 없다고 본다.
  private boolean isEmptyResult(JsonNode result) {
    boolean hasRating = result.path("rating").isNumber();
    boolean hasReviewCount = result.path("reviewCount").isNumber();
    boolean hasImage = result.path("imageUrl").isTextual();
    return !hasRating && !hasReviewCount && !hasImage;
  }

  private PlaceReviewResultDto runOne(Long placeId) {
    Place place = placeRepository.findById(placeId).orElse(null);
    if (place == null) {
      return new PlaceReviewResultDto(placeId, null, false, "존재하지 않는 장소 id입니다.",
          null, null, null, null, null, null, null, null, null, false, false, null);
    }

    try {
      JsonNode result = requestReviewAnalysis(place);

      if (isEmptyResult(result)) {
        return new PlaceReviewResultDto(placeId, place.getName(), true, null,
            null, null, null, null, null, null, null, null, null, false, false,
            textOrNull(result.path("summary")));
      }

      Integer scoreEnergy = clampScore(result.path("scoreEnergy"));
      Integer scoreImmersion = clampScore(result.path("scoreImmersion"));
      Integer scoreVibe = clampScore(result.path("scoreVibe"));
      Integer scoreAesthetic = clampScore(result.path("scoreAesthetic"));
      Integer scoreDepth = clampScore(result.path("scoreDepth"));
      Integer scorePacing = clampScore(result.path("scorePacing"));
      Double rating = result.path("rating").isNumber() ? result.path("rating").asDouble() : null;
      Integer reviewCount = result.path("reviewCount").isNumber() ? result.path("reviewCount").asInt() : null;
      String candidateImageUrl = textOrNull(result.path("imageUrl"));

      boolean imageVerified = candidateImageUrl != null && isRealImageUrl(candidateImageUrl);
      boolean imageApplied = false;

      PlaceStyle style = placeStyleRepository.findByPlace_Id(placeId).orElseGet(() ->
          PlaceStyle.builder().place(place).build());
      if (scoreEnergy != null) style.setScoreEnergy(scoreEnergy);
      if (scoreImmersion != null) style.setScoreImmersion(scoreImmersion);
      if (scoreVibe != null) style.setScoreVibe(scoreVibe);
      if (scoreAesthetic != null) style.setScoreAesthetic(scoreAesthetic);
      if (scoreDepth != null) style.setScoreDepth(scoreDepth);
      if (scorePacing != null) style.setScorePacing(scorePacing);
      style.setRating(rating);
      style.setReviewCount(reviewCount);
      style.setReviewed(true);
      placeStyleRepository.save(style);

      // 기존에 이미 이미지가 있으면(다른 소스 동기화로 채워졌을 수 있음) 덮어쓰지 않는다 - 검증
      // 안 된 AI 추정 URL로 멀쩡한 기존 사진을 잃을 위험을 막기 위함.
      if (imageVerified && (place.getImageUrl() == null || place.getImageUrl().isBlank())) {
        place.setImageUrl(candidateImageUrl);
        placeRepository.save(place);
        imageApplied = true;
      }

      return new PlaceReviewResultDto(placeId, place.getName(), true, null,
          scoreEnergy, scoreImmersion, scoreVibe, scoreAesthetic, scoreDepth, scorePacing,
          rating, reviewCount, candidateImageUrl, imageVerified, imageApplied,
          textOrNull(result.path("summary")));

    } catch (Exception e) {
      log.warn("장소 {} 리뷰 분석 실패: {}", placeId, e.getMessage());
      return new PlaceReviewResultDto(placeId, place.getName(), false, e.getMessage(),
          null, null, null, null, null, null, null, null, null, false, false, null);
    }
  }

  // 1차 검색 결과가 부실하면(rating/reviewCount/imageUrl 전부 없음) previous_response_id로 같은
  // 대화를 이어서 다른 검색어로 한 번 더 시도시킨다 - 프롬프트 문구만으로는 모델이 검색을 한 번만
  // 해보고 포기하는 걸 막을 수 없어서(2026-08-25 확인), 코드에서 직접 재시도를 강제한다.
  private JsonNode requestReviewAnalysis(Place place) {
    PlaceCategory category = place.getPlaceCategory();
    String parentCategory = category != null ? category.getParentCategory() : place.getCategory();
    String subCategory = category != null ? category.getSubCategory() : "미분류";
    String baseline = category != null
        ? "이 세부분류의 기준값: 에너지=%d, 몰입=%d, 바이브=%d, 미적=%d, 깊이=%d, 즉흥계획=%d".formatted(
            category.getScoreEnergy(), category.getScoreImmersion(), category.getScoreVibe(),
            category.getScoreAesthetic(), category.getScoreDepth(), category.getScorePacing())
        : "이 장소는 세부분류가 아직 없어서 기준값 없이 처음부터 점수를 매겨줘(50=중립 기준).";

    String prompt = PROMPT_TEMPLATE.formatted(
        place.getName(), place.getAddress() != null ? place.getAddress() : "",
        parentCategory != null ? parentCategory : "미상", subCategory, baseline, AXIS_GUIDE);

    Map<?, ?> firstResponse = callResponsesApi(prompt, null);
    JsonNode firstResult = parseJsonText(extractLastMessageText(firstResponse));

    if (!isEmptyResult(firstResult)) {
      return firstResult;
    }

    String responseId = (String) firstResponse.get("id");
    if (responseId == null) {
      return firstResult;
    }
    Map<?, ?> secondResponse = callResponsesApi(RETRY_PROMPT, responseId);
    JsonNode secondResult = parseJsonText(extractLastMessageText(secondResponse));
    return isEmptyResult(secondResult) ? firstResult : secondResult;
  }

  private Map<?, ?> callResponsesApi(String prompt, String previousResponseId) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", MODEL);
    requestBody.put("input", prompt);
    requestBody.put("tools", List.of(Map.of(
        "type", "web_search",
        "user_location", Map.of("type", "approximate", "country", "KR")
    )));
    if (previousResponseId != null) {
      requestBody.put("previous_response_id", previousResponseId);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    return restTemplate.exchange(
        RESPONSES_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class
    ).getBody();
  }

  private JsonNode parseJsonText(String text) {
    if (text == null) {
      throw new IllegalStateException("OpenAI 응답에서 텍스트를 찾지 못했습니다.");
    }
    return objectMapper.readTree(stripCodeFence(text));
  }

  @SuppressWarnings("unchecked")
  private String extractLastMessageText(Map<?, ?> response) {
    List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output");
    if (output == null) return null;
    String lastText = null;
    for (Map<String, Object> item : output) {
      if (!"message".equals(item.get("type"))) continue;
      List<Map<String, Object>> content = (List<Map<String, Object>>) item.get("content");
      if (content == null) continue;
      for (Map<String, Object> c : content) {
        Object t = c.get("text");
        if (t instanceof String s) lastText = s;
      }
    }
    return lastText;
  }

  private String stripCodeFence(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "");
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3);
      }
    }
    return trimmed.trim();
  }

  private Integer clampScore(JsonNode node) {
    if (!node.isNumber()) return null;
    int v = (int) Math.round(node.asDouble());
    return Math.max(0, Math.min(100, v));
  }

  private String textOrNull(JsonNode node) {
    return node.isTextual() ? node.asText() : null;
  }

  // AI가 준 이미지 URL이 실제로 열리는 이미지인지 HEAD 요청으로 확인한다. 타임아웃/404/이미지가
  // 아닌 응답이면 전부 false - 깨진 링크를 Place.imageUrl에 저장하지 않기 위한 방어.
  private boolean isRealImageUrl(String url) {
    try {
      HttpHeaders headers = restTemplate.headForHeaders(url);
      MediaType contentType = headers.getContentType();
      return contentType != null && "image".equals(contentType.getType());
    } catch (RestClientException | IllegalArgumentException e) {
      return false;
    }
  }
}
