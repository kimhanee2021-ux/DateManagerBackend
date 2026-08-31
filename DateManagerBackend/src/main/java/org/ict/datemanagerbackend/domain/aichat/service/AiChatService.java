package org.ict.datemanagerbackend.domain.aichat.service;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessage;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatSession;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

// OpenAI Responses API 연동 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기
// 위해 분리함(2026-08-19). 실제 구현/설계 배경 설명은 AiChatServiceImpl 참고.
public interface AiChatService {
  // <<새 채팅 세션 시작>>
  AiChatSession createSession(User user, String title);
  // <<사용자 메시지 저장 + OpenAI 응답 받아 저장, 반환값은 방금 생성된 AI 응답 메시지>>
  AiChatMessage sendMessage(User user, Long sessionId, String userText, Double lat, Double lon);
  // <<세션의 전체 메시지 이력(오래된 순)>>
  List<AiChatMessage> getMessages(User user, Long sessionId);
  // <<"지난 대화 보기" 목록 - 이 유저의 세션을 페이지네이션해서 최신순으로>>
  Page<AiChatSession> listSessions(User user, Pageable pageable);
  // <<유저 성향값 기준으로 후보 장소를 추리고, OpenAI가 그중 코스로 묶을 곳을 골라 이유와 함께 반환.
  //   lat/lon이 있으면 반경 10km 이내로만 추천(2026-08-25), 없으면 지역 필터 없이 예전 방식>>
  List<org.ict.datemanagerbackend.domain.aichat.dto.CourseRecommendationDto> recommendCourse(User user, Double lat, Double lon);
  // <<저장만 되고 안 쓰이던 의도 태그를 집계해서 가장 많이 나온 것 1개 반환(없으면 null)>>
  org.ict.datemanagerbackend.domain.aichat.dto.IntentSummaryDto getIntentSummary(User user);
  // <<관리자 페이지 - 처리 대기 중인 신고 사유 목록을 받아 한눈에 볼 수 있게 요약>>
  String summarizeReports(List<String> reasons);
  // <<장소 상세 화면 "AI 코치의 한마디" - 유저 성향과 이 장소가 왜 맞는지 짧게 설명>>
  org.ict.datemanagerbackend.domain.aichat.dto.PlaceCoachExplanationDto explainPlace(User user, Long placeId);
  // <<AI 스마트 자금 플래너 - 자연어 목표 입력을 금액/기간/카테고리/목적지로 파싱. 모호하면
  //   needsClarification=true + 되물을 질문 반환>>
  org.ict.datemanagerbackend.domain.aichat.dto.FundGoalParseDto parseFundGoal(String userText);
  // <<AI 스마트 자금 플래너 - 목표 현황을 보고 짧은 브리핑 코멘트 한 줄 생성>>
  String generateFundGoalComment(String title, long targetAmount, long currentAmount, int targetPeriodMonth);
}
