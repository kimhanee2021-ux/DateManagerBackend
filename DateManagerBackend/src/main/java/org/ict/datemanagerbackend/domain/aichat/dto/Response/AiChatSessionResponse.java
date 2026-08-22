package org.ict.datemanagerbackend.domain.aichat.dto.Response;

import java.time.LocalDateTime;

// 새 채팅 세션 생성(POST /sessions) 응답.
public record AiChatSessionResponse(Long sessionId, String title, LocalDateTime createdAt) {
}
