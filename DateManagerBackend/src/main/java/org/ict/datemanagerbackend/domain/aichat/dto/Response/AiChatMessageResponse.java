package org.ict.datemanagerbackend.domain.aichat.dto.Response;

import java.time.LocalDateTime;

// 메시지 전송(POST /messages) 응답이자, 메시지 이력 조회(GET /messages)의 목록 원소 타입.
public record AiChatMessageResponse(Long messageId, String senderType, String messageText, LocalDateTime createdAt) {
}
