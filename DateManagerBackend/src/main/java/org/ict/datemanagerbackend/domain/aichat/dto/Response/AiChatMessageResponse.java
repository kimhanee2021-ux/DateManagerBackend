package org.ict.datemanagerbackend.domain.aichat.dto.Response;

import java.time.LocalDateTime;
import java.util.List;

// 메시지 전송(POST /messages) 응답이자, 메시지 이력 조회(GET /messages)의 목록 원소 타입.
// followUpQuestions/updatedStyleAxes는 방금 받은 AI 응답에만 실리고(각각 2026-08-22, 2026-08-25
// 추가), 이력 조회에서는 항상 null.
public record AiChatMessageResponse(Long messageId, String senderType, String messageText, LocalDateTime createdAt,
                                     List<String> followUpQuestions, List<String> updatedStyleAxes) {
}
