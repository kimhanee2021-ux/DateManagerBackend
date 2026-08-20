package org.ict.datemanagerbackend.domain.aichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessage;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessageIntent;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessageScore;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatSession;
import org.ict.datemanagerbackend.domain.aichat.repository.AiChatMessageIntentRepository;
import org.ict.datemanagerbackend.domain.aichat.repository.AiChatMessageRepository;
import org.ict.datemanagerbackend.domain.aichat.repository.AiChatMessageScoreRepository;
import org.ict.datemanagerbackend.domain.aichat.repository.AiChatSessionRepository;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.weather.dto.WeatherResponse;
import org.ict.datemanagerbackend.weather.service.WeatherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API(https://api.openai.com/v1/responses) 연동 서비스 구현체.
 * admin 도메인의 interface+impl 패턴과 통일하기 위해 AiChatService 인터페이스를 분리함(2026-08-19).
 *
 * <p>이전엔 Chat Completions API(chat_memory04.ipynb 방식)로 매번 이 세션의 지난 메시지 전체를
 * messages 배열로 다시 만들어 보내는 식으로 "대화 기억"을 흉내 냈는데, Responses API는 OpenAI가
 * 서버 쪽에서 대화 맥락을 직접 기억해준다(2026-08-14, 교육 자료 갱신분 반영 — chat_completion_vs_response_rest_api07,
 * chat_memory04 최신판, main.py 참고). 그래서 매 요청마다 <b>새 유저 메시지 하나만</b> 보내면서
 * 직전 응답의 id({@link AiChatSession#getLastResponseId()})를 같이 넘기면, OpenAI가 그 이전까지의
 * 대화를 전부 기억한 채로 이어서 답한다. 우리가 대화 이력을 매번 재구성해서 보낼 필요가 없어졌다.
 *
 * <p>페르소나 설정 + 상황 정보(현재 시각/날씨)는 대화 맥락처럼 계속 이어지는 게 아니라 매 요청
 * 시점에 새로 알려줘야 하는 값이라, Responses API의 {@code instructions} 파라미터로 매번 새로
 * 보낸다(previous_response_id로 이어지는 대화 맥락과는 별개로, 그 턴의 응답 생성에만 적용됨).
 *
 * <p>근처 장소 목록은 더 이상 매 요청마다 고정으로 끼워 넣지 않는다 - Function Calling(교육자료
 * function_calling09-3)으로 {@code search_nearby_places} 도구를 등록해두면, AI가 실제로 장소를
 * 추천해야 할 때만 스스로 이 도구를 호출한다({@link #requestResponse}). 단순 잡담일 땐 장소 검색을
 * 아예 안 하게 되어 예전보다 효율적이다.
 *
 * <p>공식 openai 자바 SDK 대신 NaverPlaceSyncService 등 기존 코드와 같은 RestTemplate 직접 호출
 * 방식을 썼다 — 배운 노트북의 REST 버전(requests로 직접 POST)과 같은 구조라서 이해하기 쉽고,
 * 이 프로젝트에 이미 있는 패턴을 그대로 재사용할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatServiceImpl implements AiChatService {

  private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
  private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
  private static final String MODEL = "gpt-4o-mini";

  private static final DateTimeFormatter NOW_FORMAT =
      DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE a h시 mm분", Locale.KOREAN);

  // 페르소나를 잡아주는 고정 system 프롬프트. 컨텍스트 메시지(현재 시각/날씨)와는 역할이 달라서
  // 따로 둔다 - 이건 세션이 바뀌어도 항상 똑같은 "역할 설정"이고, 컨텍스트는 요청마다 바뀌는 "상황 정보".
  //
  // 2026-08-20 사용자 지시로 "데이트 코치/상담사" 톤에서 "실제 애인과 대화하는 느낌"으로 전환함
  // (project-date-manager-aichat-persona-and-style-matching 메모리 참고). 장소 추천은 지금은
  // search_nearby_places 도구가 준 후보 목록 안에서만 고르는 방식이지만, 유저 성향값(온보딩 파이프라인,
  // 팀원 담당)과 장소 성향값(PlaceStyle 5축, 빅데이터 분석으로 채울 예정, 아직 미착수)이 둘 다
  // 준비되면 "네 취향엔 이런 곳이 잘 맞을 것 같아" 식으로 성향 매칭 근거를 들어 추천하는 방향으로
  // 갈 것 - 지금은 그 데이터가 없어서 단순 근접 추천에 머무른다.
  private static final String PERSONA_SYSTEM_PROMPT = """
      너는 사용자와 연인처럼 다정하게 대화하는 데이트 앱 AI '데이트매니저'야. 상담사나 코치처럼 조언을
      늘어놓는 게 아니라, 실제 남자친구·여자친구랑 대화하는 것처럼 친밀하고 다정한 말투로 데이트
      장소·코스를 추천하고 데이트 관련 고민(선물, 대화 주제, 갈등 등)을 들어줘.
      - 격식 있는 존댓말 대신, 애인 사이처럼 편안하고 다정한 반말 위주 말투를 써. 딱딱하게 정보만
        나열하지 말고 챙겨주는 느낌으로 말해.
      - 답변은 3~4문장 이내로 짧고 구체적으로 해.
      - 데이트·연애와 관련 없는 질문(코딩, 시사, 숙제 등)에는 다정하게 거절하고 데이트 얘기로 화제를 돌려줘.
      - 확실하지 않은 정보(구체적인 가게 이름, 실시간 예약 가능 여부 등)는 지어내지 말고 모른다고 말해.
      """;

  // AiChatMessageIntent.intentTag로 저장을 허용할 값 목록 - LLM이 프롬프트를 무시하고 엉뚱한
  // 문자열을 내놓을 수 있어서, 화이트리스트에 없는 값은 저장하지 않고 버린다.
  private static final List<String> INTENT_TAGS = List.of(
      "PLACE_RECOMMENDATION", "GIFT_ADVICE", "CONVERSATION_TOPIC", "RELATIONSHIP_CONCERN", "GENERAL_CHAT"
  );

  // AiChatMessageScore.scoreType 목록 - PlaceStyle의 5개 성향 축과 이름을 맞춰서, 나중에 온보딩
  // 결과와 같은 잣대로 비교/합산할 수 있게 한다.
  private static final List<String> SCORE_TYPES = List.of("ENERGY", "IMMERSION", "VIBE", "AESTHETIC", "DEPTH");

  // 도구(함수) 호출 한 번당 돌려줄 근처 장소 개수. 너무 많이 붙이면 프롬프트만 길어지고 모델이
  // 오히려 헷갈려해서, 실제 추천 카드 몇 개 보여주는 정도의 개수로 제한한다.
  private static final int NEARBY_PLACE_LIMIT = 15;

  // 장소 검색 도구의 category 파라미터 값 -> 실제 Place.category 원본 값 매핑. KOPIS 공연은 "공연"이
  // 아니라 장르명 그대로, 문화행사 전시는 "전시" 대신 "문화시설"로 저장되는 것과 같은 이유
  // (2026-08-14, 큐레이션 탭에서 쓴 것과 동일한 매핑). 프론트 curationCategories.js와 값이 어긋나지
  // 않게 나중에 카테고리를 늘릴 땐 양쪽 다 같이 갱신해야 한다.
  private static final Map<String, List<String>> CATEGORY_ALIASES = Map.ofEntries(
      Map.entry("맛집", List.of("맛집")),
      Map.entry("공연", List.of(
          "공연", "서양음악(클래식)", "연극", "뮤지컬", "대중음악",
          "한국음악(국악)", "서커스/마술", "무용(서양/한국무용)", "복합", "대중무용"
      )),
      Map.entry("전시", List.of("문화시설")),
      Map.entry("박물관·미술관", List.of("박물관/미술관")),
      Map.entry("관광지", List.of("관광지")),
      Map.entry("액티비티", List.of("액티비티")),
      Map.entry("쇼핑", List.of("쇼핑")),
      Map.entry("축제", List.of("축제")),
      Map.entry("숙박", List.of("숙박")),
      // 2026-08-20 신규 - SportsSyncService가 채워 넣는 국내 프로스포츠 예정 경기(야구/축구, 추후 농구/배구)
      Map.entry("스포츠", List.of("스포츠"))
  );

  // Function Calling(교육자료 function_calling09-3) - AI가 장소 추천이 필요할 때만 스스로 호출하는
  // 도구 정의. 예전엔 buildNearbyPlacesMessage()로 매 요청마다 근처 장소 15곳을 무조건 instructions에
  // 끼워 넣었는데, 이제는 AI가 실제로 장소를 추천해야 할 때만 이 도구를 호출해서 필요한 카테고리로
  // 검색하게 바꿨다(2026-08-14) - 잡담일 땐 장소 목록을 아예 안 보내도 되니 더 효율적이다.
  private static final List<Map<String, Object>> TOOLS = List.of(
      Map.of(
          "type", "function",
          "name", "search_nearby_places",
          "description", "사용자 근처의 실제 데이트 장소를 카테고리별로 검색한다. 장소를 구체적으로 "
              + "추천하기 전에는 반드시 이 도구로 실제 존재하는 장소를 먼저 확인하고 나서 답해야 한다.",
          "parameters", Map.of(
              "type", "object",
              "properties", Map.of(
                  "category", Map.of(
                      "type", "string",
                      "enum", CATEGORY_ALIASES.keySet().stream().sorted().toList(),
                      "description", "찾고 싶은 장소의 대분류. 특정 카테고리를 원하는 게 아니면 생략해도 된다."
                  )
              ),
              "required", List.of()
          )
      )
  );

  private static final String ANALYSIS_PROMPT = """
      다음은 데이트 상담 챗봇에 사용자가 보낸 메시지야. 이 메시지를 분석해서 아래 형식의 JSON으로만
      답해(다른 설명 텍스트 없이 JSON만):
      {"intents": ["의도 태그 배열, 해당 없으면 빈 배열"], "scores": {"ENERGY": 0~100 또는 null, "IMMERSION": 0~100 또는 null, "VIBE": 0~100 또는 null, "AESTHETIC": 0~100 또는 null, "DEPTH": 0~100 또는 null}}

      의도 태그는 다음 중에서만 골라: PLACE_RECOMMENDATION(장소 추천 요청), GIFT_ADVICE(선물 고민),
      CONVERSATION_TOPIC(대화 주제 고민), RELATIONSHIP_CONCERN(연애 고민/갈등), GENERAL_CHAT(단순 잡담).
      scores는 메시지에 취향이 명확히 드러날 때만 채우고, 드러나지 않는 항목은 null로 둬.
      ENERGY는 활동적인 것을 얼마나 선호하는지, IMMERSION은 직접 참여하는 걸 얼마나 선호하는지,
      VIBE는 트렌디한 곳을 얼마나 선호하는지, AESTHETIC은 감각적인 공간을 얼마나 선호하는지,
      DEPTH는 깊이 있는 콘텐츠를 얼마나 선호하는지를 뜻해.

      사용자 메시지: "%s"
      """;

  private final AiChatSessionRepository aiChatSessionRepository;
  private final AiChatMessageRepository aiChatMessageRepository;
  private final AiChatMessageIntentRepository aiChatMessageIntentRepository;
  private final AiChatMessageScoreRepository aiChatMessageScoreRepository;
  private final WeatherService weatherService;
  private final PlaceRepository placeRepository;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${openai.api-key}")
  private String apiKey;

  /** 새 채팅 세션을 시작한다. */
  @Override
  public AiChatSession createSession(User user, String title) {
    AiChatSession session = AiChatSession.builder()
        .user(user)
        .title(title)
        .build();
    return aiChatSessionRepository.save(session);
  }

  /**
   * 세션에 사용자 메시지를 저장하고, 대화 기록 전체를 OpenAI에 보내 응답을 받은 뒤 그 응답도
   * 저장한다. 반환값은 방금 생성된 AI 응답 메시지.
   *
   * <p>lat/lon은 선택값이다 - 프론트가 위치 권한을 안 줬거나 못 구했으면 null로 넘어오고, 이 경우
   * 날씨 없이 현재 시각만 알려준다(WeatherService 호출 자체를 생략).
   */
  @Override
  public AiChatMessage sendMessage(User user, Long sessionId, String userText, Double lat, Double lon) {
    AiChatSession session = getOwnedSession(user, sessionId);

    AiChatMessage userMessage = AiChatMessage.builder()
        .session(session)
        .senderType("USER")
        .sender(user)
        .messageText(userText)
        .build();
    aiChatMessageRepository.save(userMessage);
    analyzeMessage(userMessage);

    // 상황 정보(현재 시각/날씨)는 대화 내용이 아니라 매 요청마다 새로 알려줘야 하는 값이라,
    // instructions로만 매번 새로 만들어 보낸다(대화 이력에는 안 쌓임). 근처 장소 목록은 더 이상
    // 여기서 미리 만들어 끼워 넣지 않고, AI가 필요할 때 search_nearby_places 도구를 직접 호출한다.
    String instructions = PERSONA_SYSTEM_PROMPT + "\n\n" + buildContextMessage(lat, lon);

    // 지난 대화 전체를 다시 안 보내도 된다 - previous_response_id만 넘기면 OpenAI가 이어서 기억한다.
    ResponseApiResult result = requestResponse(userText, instructions, session.getLastResponseId(), lat, lon);

    AiChatMessage aiMessage = AiChatMessage.builder()
        .session(session)
        .senderType("AI")
        .sender(null) // AI 발신이면 sender는 null (엔티티 주석 참고)
        .messageText(result.text())
        .build();
    AiChatMessage saved = aiChatMessageRepository.save(aiMessage);

    session.setLastResponseId(result.responseId());
    aiChatSessionRepository.save(session);

    return saved;
  }

  /** 세션의 전체 메시지 이력을 오래된 순으로 반환한다. */
  @Override
  public List<AiChatMessage> getMessages(User user, Long sessionId) {
    getOwnedSession(user, sessionId); // 소유권 검증(다른 유저의 세션이면 예외 발생)
    return aiChatMessageRepository.findBySession_IdOrderByCreatedAtAsc(sessionId);
  }

  private AiChatSession getOwnedSession(User user, Long sessionId) {
    Optional<AiChatSession> session = aiChatSessionRepository.findByIdAndUser_Id(sessionId, user.getId());
    return session.orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));
  }

  /**
   * "지금은 언제고 날씨는 어떤지"를 알려주는 system 메시지 내용을 만든다. GPT는 실시간 정보에
   * 접근할 수 없어서(학습 시점 기준 지식만 가짐), 이렇게 매 요청마다 현재 상태를 직접 알려줘야
   * "오늘 날씨"·"지금 몇 시" 같은 질문에 실제 값으로 답할 수 있다.
   */
  private String buildContextMessage(Double lat, Double lon) {
    StringBuilder sb = new StringBuilder("[시스템 제공 정보] 지금은 ")
        .append(LocalDateTime.now().format(NOW_FORMAT)).append("이야.");

    if (lat != null && lon != null) {
      try {
        WeatherResponse weather = weatherService.getCurrentWeather(lat, lon);
        sb.append(" 사용자 위치의 현재 날씨는 기온 ").append(weather.temp())
            .append(", ").append(weather.desc()).append("이야.");
      } catch (Exception e) {
        log.warn("챗봇 컨텍스트용 날씨 조회 실패 (lat={}, lon={})", lat, lon, e);
      }
    }

    // GPT는 "실시간 정보는 알 수 없다"고 습관적으로 발뺌하도록 강하게 학습돼 있어서, 컨텍스트로
    // 값을 줘도 시간을 물어보면 회피 답변부터 하는 경우가 많다(날씨는 상대적으로 이 습관이 덜함).
    // 그래서 "이 값은 진짜 서버 시계에서 온 정확한 값이니 그대로 답하라"고 못 박아둔다.
    sb.append(" 이 시각/날씨는 네 추측이 아니라 실제 서버 시계·기상청 API에서 방금 가져온 정확한 값이야. ")
        .append("사용자가 지금 몇 시인지, 오늘 날씨가 어떤지 물어보면 위 값을 그대로 자신 있게 답해줘. ")
        .append("'실시간 정보를 알 수 없다'거나 '기기에서 확인하라'는 식으로 답변하지 마. ")
        .append("먼저 나서서 시간·날씨 얘기를 꺼낼 필요는 없고, 물어볼 때만 답하면 돼.");
    return sb.toString();
  }

  /**
   * search_nearby_places 도구의 실제 실행부. AI가 이 도구를 호출하면 위경도 근처의 실제 장소를
   * (있으면 category로 좁혀서) 찾아 문자열로 돌려준다 - 이게 없으면 모델이 장소를 추천할 때 실제로
   * 존재하지 않는 가게 이름을 지어낼 수 있어서(할루시네이션), 실제 DB에 있는 장소만 후보로 준다.
   * 도구 반환값은 반드시 문자열이어야 해서(교육자료 규칙), 결과가 없어도 빈 문자열이 아니라
   * 상황을 설명하는 문장을 돌려준다.
   */
  private String executeSearchNearbyPlacesTool(String category, Double lat, Double lon) {
    if (lat == null || lon == null) {
      return "위치 정보가 없어서 근처 장소를 검색할 수 없어. 위치 없이 알 수 있는 범위에서만 답해야 해.";
    }

    List<String> rawCategories = CATEGORY_ALIASES.get(category);
    List<Place> nearby = rawCategories == null
        ? placeRepository.findNearestPlaces(lat, lon, NEARBY_PLACE_LIMIT)
        : placeRepository.findNearestPlaces(lat, lon, Math.max(NEARBY_PLACE_LIMIT * 20, 300)).stream()
            .filter(p -> rawCategories.contains(p.getCategory()))
            .limit(NEARBY_PLACE_LIMIT)
            .toList();

    if (nearby.isEmpty()) {
      return "근처에서 조건에 맞는 실제 장소를 못 찾았어. 없다고 솔직히 말하고, 없는 가게 이름을 지어내지 마.";
    }

    // 공연/스포츠처럼 특정 날짜에만 열리는 장소는 날짜를 안 알려주면 AI가 "이미 끝났을 수도 있는"
    // 곳을 아무 때나 갈 수 있는 것처럼 추천할 위험이 있어서(2026-08-20, 스포츠 카테고리 추가하며
    // 발견), startDate/showTimeInfo가 있으면 같이 붙여준다.
    String list = nearby.stream()
        .map(p -> "- " + p.getName() + " (" + p.getCategory()
            + (p.getAddress() != null ? ", " + p.getAddress() : "")
            + (p.getStartDate() != null ? ", " + p.getStartDate() : "")
            + (p.getShowTimeInfo() != null && !p.getShowTimeInfo().isBlank() ? " " + p.getShowTimeInfo() : "")
            + ")")
        .collect(Collectors.joining("\n"));

    return "사용자 근처의 실제 장소 목록이야:\n" + list
        + "\n장소를 구체적으로 추천할 땐 반드시 이 목록 안에 있는 이름만 골라서 말해줘. "
        + "목록에 어울리는 곳이 없으면 없다고 솔직히 말하고, 목록에 없는 가게 이름을 지어내지 마. "
        + "날짜가 붙어 있는 장소(공연, 스포츠 경기 등)는 그 날짜에만 진행되는 거니까, "
        + "[시스템 제공 정보]로 알려준 오늘 날짜와 비교해서 언제 열리는지도 같이 말해줘.";
  }

  /**
   * 사용자 메시지 하나를 별도의 OpenAI 호출로 분석해서 의도 태그(AiChatMessageIntent)와
   * 성향점수(AiChatMessageScore)를 뽑아 저장한다. 실제 채팅 응답과는 무관한 부가 기능이라,
   * 분석이 실패해도(JSON 파싱 실패, API 호출 실패 등) 로그만 남기고 넘어간다 - 분석 실패 때문에
   * 사용자가 챗봇 답변을 못 받으면 안 되니까(날씨 조회 실패를 무시하는 것과 같은 이유).
   */
  private void analyzeMessage(AiChatMessage userMessage) {
    try {
      String content = requestAnalysisCompletion(userMessage.getMessageText());
      JsonNode root = objectMapper.readTree(content);

      for (JsonNode tagNode : root.path("intents")) {
        String tag = tagNode.asText(null);
        if (tag != null && INTENT_TAGS.contains(tag)) {
          aiChatMessageIntentRepository.save(
              AiChatMessageIntent.builder().message(userMessage).intentTag(tag).build());
        }
      }

      JsonNode scores = root.path("scores");
      for (String scoreType : SCORE_TYPES) {
        JsonNode valueNode = scores.path(scoreType);
        if (valueNode.isNumber()) {
          aiChatMessageScoreRepository.save(
              AiChatMessageScore.builder()
                  .message(userMessage)
                  .scoreType(scoreType)
                  .scoreValue(valueNode.asInt())
                  .build());
        }
      }
    } catch (Exception e) {
      log.warn("메시지 의도/성향점수 분석 실패 (messageId={})", userMessage.getId(), e);
    }
  }

  /**
   * 분석 전용 OpenAI 호출. response_format을 json_object로 지정해서, 응답이 항상 파싱 가능한
   * JSON 문자열로만 오도록 강제한다(안 그러면 모델이 자연어 설명을 섞어서 답할 수 있음).
   */
  private String requestAnalysisCompletion(String userText) {
    List<Map<String, String>> messages = List.of(
        Map.of("role", "user", "content", ANALYSIS_PROMPT.formatted(userText))
    );

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", MODEL);
    requestBody.put("messages", messages);
    requestBody.put("response_format", Map.of("type", "json_object"));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    Map<?, ?> response = restTemplate.exchange(
        CHAT_COMPLETIONS_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        Map.class
    ).getBody();

    List<?> choices = (List<?>) response.get("choices");
    Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
    Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
    return (String) message.get("content");
  }

  /** requestResponse()의 반환값 - 답변 텍스트와, 다음 턴에 이어 보낼 응답 id를 함께 담는다. */
  private record ResponseApiResult(String text, String responseId) {
  }

  /**
   * OpenAI Responses API로 새 유저 메시지 하나만 보내고(대화 이력 재전송 없음), previousResponseId가
   * 있으면 같이 넘겨서 이전 대화에 이어 답하게 한다. tools(search_nearby_places)를 같이 보내서, AI가
   * 장소 검색이 필요하다고 판단하면 직접 도구를 호출하게 한다(교육자료 function_calling09-3 방식).
   *
   * <p>AI가 도구를 호출하면(응답에 type="function_call" 항목이 있으면) 우리가 그 함수를 실제로
   * 실행해서 결과를 다시 같은 대화(previous_response_id)에 이어 보내야 최종 답변 텍스트가 나온다 -
   * 그래서 이 메서드는 필요하면 OpenAI를 두 번 호출한다.
   */
  private ResponseApiResult requestResponse(String userText, String instructions, String previousResponseId,
                                             Double lat, Double lon) {
    Map<String, Object> firstRequest = new LinkedHashMap<>();
    firstRequest.put("model", MODEL);
    firstRequest.put("input", userText);
    firstRequest.put("instructions", instructions);
    firstRequest.put("tools", TOOLS);
    if (previousResponseId != null) {
      firstRequest.put("previous_response_id", previousResponseId);
    }
    Map<?, ?> firstResponse = postToResponsesApi(firstRequest);

    List<Map<?, ?>> functionCalls = extractFunctionCalls(firstResponse);
    if (functionCalls.isEmpty()) {
      return new ResponseApiResult(extractText(firstResponse), (String) firstResponse.get("id"));
    }

    // 도구 호출 결과를 모아서 같은 대화(방금 응답의 id)에 이어 보낸다. tools는 다시 안 보내도 되지만
    // (이번 요청에서 또 도구를 부를 필요는 없음), instructions는 previous_response_id로 이어지는
    // "대화 맥락"과 달리 매 요청마다 새로 안 보내면 그 턴에는 적용이 안 된다(클래스 상단 javadoc
    // 참고) - 처음엔 이걸 놓쳐서, 정작 장소를 추천하는 답변(도구 호출이 실제로 일어난 턴)에서만
    // 페르소나 말투가 안 먹히고 딱딱한 문어체로 나오는 문제가 있었다(2026-08-20 발견).
    List<Map<String, Object>> toolOutputs = new ArrayList<>();
    for (Map<?, ?> call : functionCalls) {
      String functionName = (String) call.get("name");
      String callId = (String) call.get("call_id");
      String argumentsJson = (String) call.get("arguments");

      String toolResult;
      if ("search_nearby_places".equals(functionName)) {
        String category = parseCategoryArgument(argumentsJson);
        toolResult = executeSearchNearbyPlacesTool(category, lat, lon);
      } else {
        toolResult = "알 수 없는 도구야: " + functionName;
      }

      toolOutputs.add(Map.of(
          "type", "function_call_output",
          "call_id", callId,
          "output", toolResult
      ));
    }

    Map<String, Object> secondRequest = new LinkedHashMap<>();
    secondRequest.put("model", MODEL);
    secondRequest.put("input", toolOutputs);
    secondRequest.put("instructions", instructions);
    secondRequest.put("previous_response_id", firstResponse.get("id"));
    Map<?, ?> secondResponse = postToResponsesApi(secondRequest);

    return new ResponseApiResult(extractText(secondResponse), (String) secondResponse.get("id"));
  }

  private Map<?, ?> postToResponsesApi(Map<String, Object> requestBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey); // Authorization: Bearer {apiKey}

    return restTemplate.exchange(
        RESPONSES_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        Map.class
    ).getBody();
  }

  /** response.output 배열에서 type="function_call"인 항목만 골라낸다. */
  @SuppressWarnings("unchecked")
  private List<Map<?, ?>> extractFunctionCalls(Map<?, ?> response) {
    List<?> output = (List<?>) response.get("output");
    List<Map<?, ?>> calls = new ArrayList<>();
    for (Object item : output) {
      Map<?, ?> map = (Map<?, ?>) item;
      if ("function_call".equals(map.get("type"))) {
        calls.add(map);
      }
    }
    return calls;
  }

  /** response.output 배열에서 type="message"인 항목의 첫 텍스트를 꺼낸다. 못 찾으면 안내 문구로 대체. */
  private String extractText(Map<?, ?> response) {
    List<?> output = (List<?>) response.get("output");
    for (Object item : output) {
      Map<?, ?> map = (Map<?, ?>) item;
      if ("message".equals(map.get("type"))) {
        List<?> content = (List<?>) map.get("content");
        if (content != null && !content.isEmpty()) {
          Map<?, ?> firstContent = (Map<?, ?>) content.get(0);
          Object text = firstContent.get("text");
          if (text != null) return (String) text;
        }
      }
    }
    log.warn("Responses API 응답에서 텍스트를 못 찾음: {}", response);
    return "미안해, 지금 답변을 만드는 데 문제가 생겼어. 다시 한 번 물어봐 줄래?";
  }

  /** function_call의 arguments(JSON 문자열, 예: {"category":"맛집"})에서 category 값만 꺼낸다. */
  private String parseCategoryArgument(String argumentsJson) {
    if (argumentsJson == null || argumentsJson.isBlank()) return null;
    try {
      JsonNode node = objectMapper.readTree(argumentsJson).path("category");
      return node.isMissingNode() || node.isNull() ? null : node.asText();
    } catch (Exception e) {
      log.warn("도구 호출 arguments 파싱 실패: {}", argumentsJson, e);
      return null;
    }
  }

}
