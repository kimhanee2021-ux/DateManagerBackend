package org.ict.datemanagerbackend.domain.aichat.service;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessage;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatSession;
import org.ict.datemanagerbackend.domain.user.entity.User;

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
}
