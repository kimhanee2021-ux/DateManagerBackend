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
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.ict.datemanagerbackend.domain.user.repository.UserStyleRepository;
import org.ict.datemanagerbackend.weather.dto.WeatherResponse;
import org.ict.datemanagerbackend.weather.service.WeatherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  // 페르소나를 잡아주는 system 프롬프트. 컨텍스트 메시지(현재 시각/날씨)와는 역할이 달라서
  // 따로 둔다 - 이건 세션이 바뀌어도 항상 똑같은 "역할 설정"이고, 컨텍스트는 요청마다 바뀌는 "상황 정보".
  //
  // 2026-08-20 사용자 지시로 "데이트 코치/상담사" 톤에서 "실제 애인과 대화하는 느낌"으로 전환함
  // (project-date-manager-aichat-persona-and-style-matching 메모리 참고). 장소 추천은 지금은
  // search_nearby_places 도구가 준 후보 목록 안에서만 고르는 방식이지만, 유저 성향값(온보딩 파이프라인,
  // 팀원 담당)과 장소 성향값(PlaceStyle 5축, 빅데이터 분석으로 채울 예정, 아직 미착수)이 둘 다
  // 준비되면 "네 취향엔 이런 곳이 잘 맞을 것 같아" 식으로 성향 매칭 근거를 들어 추천하는 방향으로
  // 갈 것 - 지금은 그 데이터가 없어서 단순 근접 추천에 머무른다.
  //
  // 2026-08-22 - 프론트(AiChatTab.jsx의 resolveCoachByGender)는 이미 유저 성별에 따라 서연/도윤
  // 이미지·이름을 다르게 보여주는데, 정작 여기 프롬프트는 성별 상관없이 "데이트매니저"라는 이름의
  // 똑같은 문자열 하나였다 - AI가 자기 이름조차 모르는 상태였던 것. 프론트와 동일한 기준(FEMALE
  // 유저 -> 도윤/남성스러운 톤, 그 외(MALE/UNKNOWN) -> 서연/여성스러운 톤)으로 프롬프트 자체를
  // 성별별로 분리하고, 각 프롬프트에 코치 이름을 명시해서 AI가 그 이름으로 자신을 지칭하게 했다.
  private static final String BASE_PERSONA_RULES = """
      - 답변은 3~4문장 이내로 짧고 구체적으로 해.
      - 데이트·연애와 관련 없는 질문(코딩, 시사, 숙제 등)에는 다정하게 거절하고 데이트 얘기로 화제를 돌려줘.
      - 확실하지 않은 정보(구체적인 가게 이름, 실시간 예약 가능 여부 등)는 지어내지 말고 모른다고 말해.
      """;

  // 남성 사용자·기본값(성별 미상)에게 붙는 코치 - 프론트에서도 이 경우 서연 코치 이미지를 보여준다.
  private static final String SEOYEON_PERSONA_PROMPT = """
      너는 사용자의 여자친구처럼 다정하게 대화하는 AI 데이트 코치 '서연'이야. 상담사나 코치처럼 조언을
      늘어놓는 게 아니라, 실제 여자친구랑 대화하는 것처럼 친밀하고 다정한 말투로 데이트 장소·코스를
      추천하고 데이트 관련 고민(선물, 대화 주제, 갈등 등)을 들어줘.
      - 격식 있는 존댓말 대신, 애인 사이처럼 편안하고 다정한 반말 위주 말투를 쓰되, 살짝 상냥하고
        여성스러운 어투(부드러운 어미, 다정하게 챙겨주는 뉘앙스)를 자연스럽게 섞어. 딱딱하게 정보만
        나열하지 말고 챙겨주는 느낌으로 말해.
      """ + BASE_PERSONA_RULES;

  // 여성 사용자에게 붙는 코치 - 프론트에서 도윤 코치 이미지를 보여주는 경우와 동일 기준.
  private static final String DOYOON_PERSONA_PROMPT = """
      너는 사용자의 남자친구처럼 다정하게 대화하는 AI 데이트 코치 '도윤'이야. 상담사나 코치처럼 조언을
      늘어놓는 게 아니라, 실제 남자친구랑 대화하는 것처럼 친밀하고 다정한 말투로 데이트 장소·코스를
      추천하고 데이트 관련 고민(선물, 대화 주제, 갈등 등)을 들어줘.
      - 격식 있는 존댓말 대신, 애인 사이처럼 편안하고 다정한 반말 위주 말투를 쓰되, 살짝 든든하고
        남성스러운 어투(자신감 있게 리드하는 뉘앙스, 다정하지만 씩씩한 느낌)를 자연스럽게 섞어. 딱딱하게
        정보만 나열하지 말고 챙겨주는 느낌으로 말해.
      """ + BASE_PERSONA_RULES;

  /** 프론트(resolveCoachByGender)와 동일한 기준 - FEMALE만 도윤(남성 톤), 그 외는 서연(여성 톤). */
  private String resolvePersonaPrompt(User user) {
    String gender = user.getGender() == null ? "" : user.getGender().trim().toUpperCase(Locale.ROOT);
    return "FEMALE".equals(gender) ? DOYOON_PERSONA_PROMPT : SEOYEON_PERSONA_PROMPT;
  }

  // 축(50점 기준 높음/낮음)별로 자연어로 풀어줄 설명 - AiChatTab.jsx의 AXIS_QUESTION_POOL과
  // 같은 축 구성(온보딩 6대 성향)을 그대로 따른다.
  private static final Map<String, String> AXIS_HIGH_DESC = Map.of(
      "energy", "활동적인 걸 선호해", "immersion", "직접 참여하는 체험을 선호해",
      "vibe", "트렌디한 곳을 선호해", "aesthetic", "감각적인 공간을 선호해",
      "pacing", "즉흥적인 걸 선호해", "depth", "깊이 있는 콘텐츠를 선호해"
  );
  private static final Map<String, String> AXIS_LOW_DESC = Map.of(
      "energy", "차분하고 여유로운 걸 선호해", "immersion", "가볍게 즐기는 걸 선호해",
      "vibe", "편안하고 익숙한 분위기를 선호해", "aesthetic", "꾸밈없이 편한 곳을 선호해",
      "pacing", "미리 계획하는 걸 선호해", "depth", "가볍고 부담 없는 걸 선호해"
  );

  /**
   * 유저 성향값(온보딩 결과)을 자연어 한두 문장으로 풀어서 대화 컨텍스트에 얹는다(2026-08-22
   * 추가). 지금까지는 대화 시작 전 추천 질문 고를 때만 성향값을 썼는데, 정작 대화 내내 AI가
   * "이 유저가 어떤 취향인지"는 전혀 몰랐다 - 장소별 실성향점수가 없어 정량적 매칭 근거까지는
   * 못 대더라도, 이 정도는 장소 데이터 없이 바로 반영할 수 있는 부분이라 먼저 붙인다. 성향값이
   * 하나도 없으면(온보딩 전) 빈 문자열을 반환해서 그냥 언급 안 하게 한다.
   */
  private String buildPersonaStyleContext(User user) {
    UserStyle style = userStyleRepository.findById(user.getId()).orElse(null);
    if (style == null) return "";

    // 2026-08-22 - UserStyle 내부 저장값이 Double로 바뀌어서(실시간 갱신 엔진용), 여기선
    // 소수점을 반올림해서(UserStyle.round()) 예전처럼 정수 맵으로 만든다.
    Map<String, Integer> scores = new LinkedHashMap<>();
    Integer energy = UserStyle.round(style.getInitEnergy());
    Integer immersion = UserStyle.round(style.getInitImmersion());
    Integer vibe = UserStyle.round(style.getInitVibe());
    Integer aesthetic = UserStyle.round(style.getInitAesthetic());
    Integer pacing = UserStyle.round(style.getInitPacing());
    Integer depth = UserStyle.round(style.getInitDepth());
    if (energy != null) scores.put("energy", energy);
    if (immersion != null) scores.put("immersion", immersion);
    if (vibe != null) scores.put("vibe", vibe);
    if (aesthetic != null) scores.put("aesthetic", aesthetic);
    if (pacing != null) scores.put("pacing", pacing);
    if (depth != null) scores.put("depth", depth);
    if (scores.isEmpty()) return "";

    List<String> traits = scores.entrySet().stream()
        .sorted((a, b) -> Math.abs(b.getValue() - 50) - Math.abs(a.getValue() - 50))
        .limit(3)
        .map(e -> e.getValue() >= 50 ? AXIS_HIGH_DESC.get(e.getKey()) : AXIS_LOW_DESC.get(e.getKey()))
        .toList();

    return "\n\n[시스템 제공 정보] 이 유저의 성향 참고 - " + String.join(", ", traits)
        + ". 자연스럽게 대화하면서 이 취향에 맞게 추천하거나 공감해줘(성향을 그대로 읽어주듯 말하지는 마).";
  }

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

  // 세션 제목 자동 요약용(2026-08-22 추가) - "{코치이름} 데이트 상담"이라는 똑같은 제목만 뜨던
  // 문제를 고치려고, 첫 턴이 끝나면 그 대화 내용으로 짧은 제목을 만든다.
  private static final String TITLE_PROMPT = """
      다음은 사용자와 AI 데이트 코치의 대화 첫 turn이야. 이 대화 내용을 대표하는 아주 짧은 제목을
      만들어줘. 설명이나 따옴표 없이 제목 텍스트만 출력해. 10자 내외로, 명사형으로 끝내고 마침표는
      쓰지 마.

      사용자: "%s"
      AI: "%s"
      """;

  // 홈탭 "최근 관심사" 인사이트 한 줄 생성용(2026-08-27 추가) - 원래는 의도 태그 1위만 뽑아서
  // "OO를 가장 많이 물어봤어요"라고 기계적으로 채워 넣었는데, 전체 분포를 보여주고 자연스러운
  // 문장으로 다듬어달라고 하면 훨씬 사람이 쓴 것 같은 코멘트가 나온다.
  private static final String INTENT_INSIGHT_PROMPT = """
      다음은 한 유저가 AI 데이트 코치와 나눈 대화에서 뽑힌 대화 주제별 횟수야:
      %s

      이 데이터를 보고, 유저의 최근 관심사를 짚어주는 자연스러운 한 문장 코멘트를 만들어줘.
      홈 화면 인사이트 칩에 들어갈 짧은 문장이야. 조건:
      - 30자 내외, 존댓말체("~하고 계시네요" 등)
      - 이모지, 따옴표, 설명 없이 문장 하나만 출력
      - 숫자(횟수)는 언급하지 말고 경향만 자연스럽게 표현
      """;

  // 대화 중 후속 질문 추천용(2026-08-22 추가) - 사용자가 매번 처음부터 뭘 물어볼지 고민하지 않아도
  // 되게, 방금 오간 대화에 자연스럽게 이어질 질문을 AI가 직접 골라서 클릭 한 번으로 보낼 수 있게 한다.
  private static final String FOLLOWUP_PROMPT = """
      다음은 AI 데이트 코치와 사용자가 방금 나눈 대화 한 턴이야. 이 흐름에 자연스럽게 이어질 만한
      후속 질문을 "사용자 입장에서" 2~3개 만들어줘. 아래 형식의 JSON으로만 답해(다른 설명 없이):
      {"followUps": ["질문1", "질문2", "질문3"]}
      각 질문은 15자~25자 내외로 짧게, 사용자가 코치에게 물어볼 법한 자연스러운 말투로 써.

      사용자: "%s"
      AI: "%s"
      """;

  // "AI 코스 추천"(홈탭 배너, 2026-08-25 추가)용 - 매칭점수 상위 후보를 우리가 먼저 추려서 주고,
  // 그중 실제로 코스로 묶을 4곳만 AI가 고르게 한다(전체 장소 중에서 AI가 직접 고르게 하면 존재하지
  // 않는 장소를 지어낼 위험이 있어서, 반드시 후보 목록 안에서만 고르도록 프롬프트로 제한한다).
  private static final String COURSE_RECOMMENDATION_PROMPT = """
      아래는 사용자의 성향값(6개 축, 0~100)과 후보 장소 목록이야. 이 사용자 성향에 가장 잘 어울리는
      "하루 데이트 코스"로 묶을 장소 4곳을 후보 목록 "안에서만" 골라줘. 아래 형식의 JSON으로만 답해
      (다른 설명 없이):
      {"picks": [{"id": 123, "reason": "왜 이 장소를 골랐는지 20자 내외 한 줄"}]}

      규칙:
      - 반드시 4곳을 고르고, id는 후보 목록에 있는 값만 사용해(후보에 없는 id를 지어내지 마).
      - 후보 목록은 이미 사용자 위치 반경 10km 이내로 걸러져 있으니 지역은 신경 안 써도 돼.
      - category가 겹치지 않게 최대한 다양하게 골라줘(같은 category만 4곳 고르지 마. 예: 콘서트
        4개처럼).

      [사용자 성향]
      %s

      [후보 장소 목록]
      %s
      """;

  private final AiChatSessionRepository aiChatSessionRepository;
  private final AiChatMessageRepository aiChatMessageRepository;
  private final AiChatMessageIntentRepository aiChatMessageIntentRepository;
  private final AiChatMessageScoreRepository aiChatMessageScoreRepository;
  private final WeatherService weatherService;
  private final PlaceRepository placeRepository;
  private final UserStyleRepository userStyleRepository;
  private final org.ict.datemanagerbackend.domain.user.service.UserStyleUpdateService userStyleUpdateService;
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
    // lastResponseId가 아직 없다는 건 이번이 이 세션의 첫 턴이라는 뜻 - "지난 대화 보기" 목록에
    // 항상 "{코치이름} 데이트 상담"이라는 똑같은 제목만 뜨던 문제(2026-08-22 발견)를 고치려고,
    // 첫 턴이 끝난 직후 대화 내용을 요약해서 진짜 제목으로 바꿔치기한다(챗GPT/제미나이와 동일한 패턴).
    boolean isFirstTurn = session.getLastResponseId() == null;

    AiChatMessage userMessage = AiChatMessage.builder()
        .session(session)
        .senderType("USER")
        .sender(user)
        .messageText(userText)
        .build();
    aiChatMessageRepository.save(userMessage);
    List<String> updatedStyleAxes = analyzeMessage(userMessage);

    // 상황 정보(현재 시각/날씨)는 대화 내용이 아니라 매 요청마다 새로 알려줘야 하는 값이라,
    // instructions로만 매번 새로 만들어 보낸다(대화 이력에는 안 쌓임). 근처 장소 목록은 더 이상
    // 여기서 미리 만들어 끼워 넣지 않고, AI가 필요할 때 search_nearby_places 도구를 직접 호출한다.
    String instructions = resolvePersonaPrompt(user) + "\n\n" + buildContextMessage(lat, lon)
        + buildPersonaStyleContext(user);

    // 지난 대화 전체를 다시 안 보내도 된다 - previous_response_id만 넘기면 OpenAI가 이어서 기억한다.
    ResponseApiResult result = requestResponse(userText, instructions, session.getLastResponseId(), lat, lon);

    AiChatMessage aiMessage = AiChatMessage.builder()
        .session(session)
        .senderType("AI")
        .sender(null) // AI 발신이면 sender는 null (엔티티 주석 참고)
        .messageText(result.text())
        .build();
    AiChatMessage saved = aiChatMessageRepository.save(aiMessage);
    if (!updatedStyleAxes.isEmpty()) {
      saved.setUpdatedStyleAxes(updatedStyleAxes);
    }

    session.setLastResponseId(result.responseId());
    if (isFirstTurn) {
      // 제목 요약이 실패해도(네트워크 오류, 파싱 실패 등) 방금 받은 실제 채팅 응답까지 날아가면
      // 안 되니 analyzeMessage()와 동일하게 실패를 삼키고 로그만 남긴다.
      try {
        String summarizedTitle = requestTitleCompletion(userText, result.text());
        if (summarizedTitle != null && !summarizedTitle.isBlank()) {
          session.setTitle(summarizedTitle);
        }
      } catch (Exception e) {
        log.warn("세션 제목 요약 실패 (sessionId={})", session.getId(), e);
      }
    }
    aiChatSessionRepository.save(session);

    // 후속 질문 추천(2026-08-22 추가) - DB에는 안 남기고 이번 응답에만 실어 보낸다. 실패해도
    // 실제 채팅 응답에는 영향 없게 analyzeMessage()와 동일하게 실패를 삼킨다.
    try {
      saved.setFollowUpQuestions(requestFollowUpQuestions(userText, result.text()));
    } catch (Exception e) {
      log.warn("후속 질문 추천 실패 (sessionId={})", session.getId(), e);
    }

    return saved;
  }

  /** 세션의 전체 메시지 이력을 오래된 순으로 반환한다. */
  @Override
  public List<AiChatMessage> getMessages(User user, Long sessionId) {
    getOwnedSession(user, sessionId); // 소유권 검증(다른 유저의 세션이면 예외 발생)
    return aiChatMessageRepository.findBySession_IdOrderByCreatedAtAsc(sessionId);
  }

  /**
   * "지난 대화 보기" 목록. 로그아웃 후 다른 브라우저/기기로 들어오면 프론트가 들고 있던
   * localStorage의 마지막 세션 id를 못 찾는데, 그것과 무관하게 DB에 저장된 이 유저의 세션을
   * 보여줘서 이어서 대화할 수 있게 한다(2026-08-22 추가). 세션이 계속 쌓일 걸 감안해 페이지네이션.
   */
  @Override
  public Page<AiChatSession> listSessions(User user, Pageable pageable) {
    return aiChatSessionRepository.findByUser_Id(user.getId(), pageable);
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

  // SCORE_TYPES(ENERGY 등)와 짝을 이루는 한글 이름 - "성향이 이렇게 바뀌었어요" 안내용.
  private static final Map<String, String> AXIS_KOREAN_NAME = Map.of(
      "ENERGY", "에너지", "IMMERSION", "몰입감", "VIBE", "분위기", "AESTHETIC", "심미감", "DEPTH", "깊이"
  );

  /**
   * 사용자 메시지 하나를 별도의 OpenAI 호출로 분석해서 의도 태그(AiChatMessageIntent)와
   * 성향점수(AiChatMessageScore)를 뽑아 저장한다. 실제 채팅 응답과는 무관한 부가 기능이라,
   * 분석이 실패해도(JSON 파싱 실패, API 호출 실패 등) 로그만 남기고 넘어간다 - 분석 실패 때문에
   * 사용자가 챗봇 답변을 못 받으면 안 되니까(날씨 조회 실패를 무시하는 것과 같은 이유).
   *
   * @return 이번 메시지로 UserStyle이 실시간 갱신된 축의 한글 이름 목록(2026-08-25 추가) - 예전엔
   *     이 갱신이 화면에 전혀 안 보이고 조용히 일어났는데, 이제 챗봇 응답에 같이 실어서 보여준다.
   */
  private List<String> analyzeMessage(AiChatMessage userMessage) {
    List<String> updatedAxes = new ArrayList<>();
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
          int scoreValue = valueNode.asInt();
          aiChatMessageScoreRepository.save(
              AiChatMessageScore.builder()
                  .message(userMessage)
                  .scoreType(scoreType)
                  .scoreValue(scoreValue)
                  .build());
          // AI챗에서 명시적으로 언급된 성향("강함" 신호, α=0.10)을 UserStyle 실시간 갱신 엔진에도
          // 반영한다(2026-08-22, backup/user-style 포팅) - 위 저장은 메시지별 분석 기록이고,
          // 이건 그 값을 누적해서 유저의 실제 성향 점수 자체를 서서히 움직이는 별도 단계다.
          try {
            userStyleUpdateService.applyAiChatMention(userMessage.getSender().getId(), scoreType, scoreValue);
            updatedAxes.add(AXIS_KOREAN_NAME.getOrDefault(scoreType, scoreType));
          } catch (Exception ex) {
            log.warn("성향값 실시간 갱신 실패 (messageId={}, axis={})", userMessage.getId(), scoreType, ex);
          }
        }
      }
    } catch (Exception e) {
      log.warn("메시지 의도/성향점수 분석 실패 (messageId={})", userMessage.getId(), e);
    }
    return updatedAxes;
  }

  /**
   * 분석/제목 요약/후속 질문 세 곳에서 거의 똑같은 "Chat Completions 호출해서 첫 응답 텍스트만
   * 꺼내기" 보일러플레이트를 복붙하고 있었다(2026-08-22 발견) - 한 곳으로 모았다. jsonMode가
   * true면 response_format을 json_object로 강제해서, 응답이 항상 파싱 가능한 JSON 문자열로만
   * 오도록 한다(안 그러면 모델이 자연어 설명을 섞어서 답할 수 있음).
   */
  private String requestChatCompletionText(String prompt, boolean jsonMode) {
    List<Map<String, String>> messages = List.of(
        Map.of("role", "user", "content", prompt)
    );

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", MODEL);
    requestBody.put("messages", messages);
    if (jsonMode) {
      requestBody.put("response_format", Map.of("type", "json_object"));
    }

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

  /** 사용자 메시지 하나를 분석해서 의도/성향점수 JSON을 받아온다. */
  private String requestAnalysisCompletion(String userText) {
    return requestChatCompletionText(ANALYSIS_PROMPT.formatted(userText), true);
  }

  /**
   * 세션 첫 턴을 요약해서 짧은 제목을 만든다. 순수 텍스트 응답이라 jsonMode는 false. 모델이
   * 실수로 앞뒤에 따옴표를 붙이는 경우가 있어 마지막에 한 번 벗겨준다.
   */
  private String requestTitleCompletion(String userText, String aiText) {
    String content = requestChatCompletionText(TITLE_PROMPT.formatted(userText, aiText), false);
    if (content == null) return null;
    return content.trim().replaceAll("^[\"']|[\"']$", "");
  }

  /** 방금 오간 대화 한 턴을 보고 후속 질문 2~3개를 뽑는다. */
  private List<String> requestFollowUpQuestions(String userText, String aiText) {
    String content = requestChatCompletionText(FOLLOWUP_PROMPT.formatted(userText, aiText), true);
    if (content == null) return List.of();

    JsonNode root = objectMapper.readTree(content);
    List<String> followUps = new ArrayList<>();
    for (JsonNode node : root.path("followUps")) {
      String text = node.asText(null);
      if (text != null && !text.isBlank()) followUps.add(text.trim());
    }
    return followUps;
  }

  /**
   * "AI 코스 추천" - 유저 성향값과 6개 축 거리가 가까운 장소를 우리 쪽에서 먼저 15곳으로 추리고
   * (매칭점수 계산은 프론트 placeMatching.js의 pickMatchingAxes와 같은 방식을 서버에서 장소 단위로
   * 적용한 것), 그중 4곳을 OpenAI가 코스로 묶어 고르게 한다. OpenAI 호출/파싱이 실패하면 매칭점수
   * 상위 4곳으로 조용히 대체한다(챗봇의 다른 부가 기능들과 같은 원칙 - 실패해도 화면이 비면 안 됨).
   */
  @Override
  public List<org.ict.datemanagerbackend.domain.aichat.dto.CourseRecommendationDto> recommendCourse(
      User user, Double lat, Double lon) {
    org.ict.datemanagerbackend.domain.user.entity.UserStyle style =
        userStyleRepository.findById(user.getId()).orElse(null);

    Map<String, Integer> userScores = new LinkedHashMap<>();
    userScores.put("energy", styleAxisOrDefault(style == null ? null : style.getInitEnergy()));
    userScores.put("immersion", styleAxisOrDefault(style == null ? null : style.getInitImmersion()));
    userScores.put("vibe", styleAxisOrDefault(style == null ? null : style.getInitVibe()));
    userScores.put("aesthetic", styleAxisOrDefault(style == null ? null : style.getInitAesthetic()));
    userScores.put("depth", styleAxisOrDefault(style == null ? null : style.getInitDepth()));
    userScores.put("pacing", styleAxisOrDefault(style == null ? null : style.getInitPacing()));

    List<Place> pool;
    if (lat != null && lon != null) {
      // 내 위치 반경 10km 이내로만 추천(2026-08-25 추가) - 예전엔 지역 필터가 전혀 없어서 서울/
      // 부산/제주가 한 코스에 섞여 나오는 문제가 있었다("지역이 너무 넓다" 실사용 피드백). 위경도
      // 사각형으로 넉넉히 1차로 추린 뒤, haversine으로 정확한 원형 10km만 다시 거른다 - 카테고리별로
      // 나눠 모으던 예전 방식(다양성 확보용)은 이제 필요 없다. 반경 안에 있는 장소들 자체가 이미
      // 카테고리가 섞여 있고, 카테고리 쏠림 방지 로직은 아래에서 그대로 유지된다.
      double latDelta = 10.0 / 111.32; // 위도 1도 ≈ 111.32km
      double lonDelta = 10.0 / (111.32 * Math.cos(Math.toRadians(lat)));
      List<Place> boxCandidates = placeRepository.findByLatitudeBetweenAndLongitudeBetween(
          lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta,
          org.springframework.data.domain.PageRequest.of(0, 400));
      pool = boxCandidates.stream()
          .filter(p -> haversineMeters(lat, lon, p.getLatitude(), p.getLongitude()) <= 10000)
          .toList();
    } else {
      // 위치 정보가 없으면(권한 거부 등) 예전 방식(카테고리별로 나눠 모으기)으로 대체.
      pool = new ArrayList<>();
      org.springframework.data.domain.Sort byIdDesc =
          org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id");
      for (String category : List.of("맛집", "전시", "박물관·미술관", "관광지", "액티비티", "쇼핑")) {
        pool.addAll(placeRepository.searchByCategoryIn(
            CATEGORY_ALIASES.get(category), null, "", null, null, null, null, false, false, null, false,
            org.springframework.data.domain.PageRequest.of(0, 20, byIdDesc)).getContent());
      }
      for (String category : List.of("공연", "숙박")) {
        pool.addAll(placeRepository.searchByCategoryIn(
            CATEGORY_ALIASES.get(category), null, "", null, null, null, null, false, false, null, false,
            org.springframework.data.domain.PageRequest.of(0, 8, byIdDesc)).getContent());
      }
    }

    record Candidate(Place place, org.ict.datemanagerbackend.domain.place.entity.PlaceCategory category, int matchScore) {}

    List<Candidate> scored = pool.stream()
        .map(p -> {
          org.ict.datemanagerbackend.domain.place.entity.PlaceCategory c = p.getPlaceCategory();
          if (c == null) return null;
          int score = (100 - Math.abs(placeAxisOrDefault(c.getScoreEnergy()) - userScores.get("energy")))
              + (100 - Math.abs(placeAxisOrDefault(c.getScoreImmersion()) - userScores.get("immersion")))
              + (100 - Math.abs(placeAxisOrDefault(c.getScoreVibe()) - userScores.get("vibe")))
              + (100 - Math.abs(placeAxisOrDefault(c.getScoreAesthetic()) - userScores.get("aesthetic")))
              + (100 - Math.abs(placeAxisOrDefault(c.getScoreDepth()) - userScores.get("depth")))
              + (100 - Math.abs(placeAxisOrDefault(c.getScorePacing()) - userScores.get("pacing")));
          return new Candidate(p, c, score);
        })
        .filter(java.util.Objects::nonNull)
        .toList();

    // 같은 카테고리가 매칭점수 상위를 통째로 차지하는 걸 막는다(2026-08-25) - PlaceCategory 점수는
    // 세부분류 단위라 같은 세부분류 장소는 전부 점수가 동일해서, 그냥 상위 15개를 자르면 한
    // 카테고리로만 몰릴 수 있다(실제 테스트로 발견 - 박물관/미술관 4곳만 추천됨). 카테고리별 상위
    // 3개까지만 남기고 그중에서 전체 상위를 추린다.
    List<Candidate> ranked = scored.stream()
        .collect(Collectors.groupingBy(c -> c.place().getCategory()))
        .values().stream()
        .flatMap(group -> group.stream()
            .sorted((a, b) -> b.matchScore() - a.matchScore())
            .limit(3))
        .sorted((a, b) -> b.matchScore() - a.matchScore())
        .limit(16)
        .toList();

    if (ranked.isEmpty()) {
      return List.of();
    }

    Map<Long, Candidate> candidateById = ranked.stream()
        .collect(Collectors.toMap(c -> c.place().getId(), c -> c));

    try {
      String userScoreText = userScores.entrySet().stream()
          .map(e -> e.getKey() + ": " + e.getValue())
          .collect(Collectors.joining(", "));
      String candidateText = ranked.stream()
          .map(c -> "id:%d name:%s category:%s address:%s".formatted(
              c.place().getId(), c.place().getName(), c.place().getCategory(),
              c.place().getAddress() != null ? c.place().getAddress() : ""))
          .collect(Collectors.joining("\n"));

      String content = requestChatCompletionText(
          COURSE_RECOMMENDATION_PROMPT.formatted(userScoreText, candidateText), true);
      JsonNode root = objectMapper.readTree(content);

      List<org.ict.datemanagerbackend.domain.aichat.dto.CourseRecommendationDto> result = new ArrayList<>();
      for (JsonNode pick : root.path("picks")) {
        if (!pick.path("id").isNumber()) continue;
        Candidate candidate = candidateById.get(pick.path("id").asLong());
        if (candidate == null) continue; // AI가 후보 목록에 없는 id를 지어낸 경우 방어
        result.add(new org.ict.datemanagerbackend.domain.aichat.dto.CourseRecommendationDto(
            candidate.place().getId(), candidate.place().getName(), candidate.place().getCategory(),
            candidate.category().getSubCategory(), candidate.place().getAddress(),
            candidate.place().getImageUrl(), pick.path("reason").asText("")));
      }
      if (!result.isEmpty()) {
        return result;
      }
    } catch (Exception e) {
      log.warn("AI 코스 추천 실패, 매칭점수 상위로 대체 (userId={})", user.getId(), e);
    }

    return ranked.stream()
        .limit(4)
        .map(c -> new org.ict.datemanagerbackend.domain.aichat.dto.CourseRecommendationDto(
            c.place().getId(), c.place().getName(), c.place().getCategory(),
            c.category().getSubCategory(), c.place().getAddress(), c.place().getImageUrl(),
            "성향에 잘 맞는 곳이에요"))
        .toList();
  }

  private static final String REPORT_SUMMARY_PROMPT = """
      아래는 서비스에 접수된 처리 대기 중인 신고 사유 목록이야. 관리자가 한눈에 파악할 수 있게
      요약해줘. 비슷한 유형끼리 묶어서 "- 유형: 건수, 짧은 설명" 형식의 불릿 목록으로, 전체
      3~5줄 이내로 작성해. 설명 없이 목록만 출력해.

      [신고 사유 목록]
      %s
      """;

  /** 관리자 페이지 "신고 요약" - 신고 건수가 늘어날수록 사유를 하나씩 읽기 번거로워서 추가(2026-08-25). */
  @Override
  public String summarizeReports(List<String> reasons) {
    if (reasons.isEmpty()) return "";
    String reasonText = reasons.stream()
        .map(r -> "- " + r)
        .collect(Collectors.joining("\n"));
    return requestChatCompletionText(REPORT_SUMMARY_PROMPT.formatted(reasonText), false).trim();
  }

  private int styleAxisOrDefault(Double value) {
    return value != null ? (int) Math.round(value) : 50;
  }

  private int placeAxisOrDefault(Integer value) {
    return value != null ? value : 50;
  }

  /** "AI 코스 추천" 반경 필터용 두 좌표 사이 거리(미터). 좌표가 없는 장소는 무조건 범위 밖으로 취급. */
  private double haversineMeters(double lat1, double lon1, Double lat2, Double lon2) {
    if (lat2 == null || lon2 == null) return Double.MAX_VALUE;
    double earthRadius = 6371000;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * earthRadius * Math.asin(Math.sqrt(a));
  }

  // INTENT_TAGS와 짝을 이루는 한글 라벨 - "최근 관심사" 칩에 태그 코드 그대로 노출하면 안 되니.
  private static final Map<String, String> INTENT_LABELS = Map.of(
      "PLACE_RECOMMENDATION", "장소 추천",
      "GIFT_ADVICE", "선물 고민",
      "CONVERSATION_TOPIC", "대화 주제 고민",
      "RELATIONSHIP_CONCERN", "연애 고민",
      "GENERAL_CHAT", "일상 대화"
  );

  /**
   * 저장만 되고 안 쓰이던 의도 태그(AiChatMessageIntent)를 집계해서 재활용한다. 전체 분포를
   * OpenAI에 한 번 더 보내 자연어 인사이트 문장을 붙이고, 실패하면(네트워크 오류 등) insight만
   * null로 남겨서 프론트가 기존 템플릿 문구로 대체하게 한다 - 다른 부가 AI 기능들과 동일한
   * "실패해도 화면이 비면 안 된다" 원칙.
   */
  @Override
  public org.ict.datemanagerbackend.domain.aichat.dto.IntentSummaryDto getIntentSummary(User user) {
    List<Object[]> counts = aiChatMessageIntentRepository.countIntentTagsByUserId(user.getId());
    if (counts.isEmpty()) return null;

    Object[] top = counts.get(0);
    String tag = (String) top[0];
    long count = ((Number) top[1]).longValue();

    String insight = null;
    try {
      insight = requestIntentInsight(counts);
    } catch (Exception e) {
      log.warn("의도 요약 인사이트 생성 실패 (userId={})", user.getId(), e);
    }

    return new org.ict.datemanagerbackend.domain.aichat.dto.IntentSummaryDto(
        tag, INTENT_LABELS.getOrDefault(tag, tag), count, insight);
  }

  /** 의도 태그 분포([tag, count] 목록)를 사람이 읽는 텍스트로 바꿔서 인사이트 프롬프트에 채워 넣는다. */
  private String requestIntentInsight(List<Object[]> counts) {
    String distribution = counts.stream()
        .map(row -> {
          String tag = (String) row[0];
          long count = ((Number) row[1]).longValue();
          return "%s: %d회".formatted(INTENT_LABELS.getOrDefault(tag, tag), count);
        })
        .collect(java.util.stream.Collectors.joining(", "));

    String content = requestChatCompletionText(INTENT_INSIGHT_PROMPT.formatted(distribution), false);
    if (content == null || content.isBlank()) return null;
    return content.trim().replaceAll("^[\"']|[\"']$", "");
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
