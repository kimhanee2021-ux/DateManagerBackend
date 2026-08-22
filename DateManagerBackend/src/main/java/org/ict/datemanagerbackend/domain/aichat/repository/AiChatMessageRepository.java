package org.ict.datemanagerbackend.domain.aichat.repository;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

  // findBySession_IdOrderByCreatedAtAsc(Long) -> "WHERE session_id = ? ORDER BY created_at ASC"
  // 대화 순서 그대로(오래된 메시지부터) 가져와야 OpenAI에 보낼 messages 배열 순서도 맞고,
  // 화면에 뿌릴 때도 위에서부터 아래로 자연스럽게 읽힌다.
  List<AiChatMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);

  // 빈 세션(메시지 하나도 없는 세션) 정리용 - 2026-08-22, 탭만 열어도 세션이 생기던 예전 버그로
  // 쌓인 데이터를 한 번 정리하는 데 씀.
  boolean existsBySession_Id(Long sessionId);

}
