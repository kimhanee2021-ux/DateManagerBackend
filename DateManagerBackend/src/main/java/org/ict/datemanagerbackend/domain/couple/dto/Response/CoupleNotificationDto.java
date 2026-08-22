package org.ict.datemanagerbackend.domain.couple.dto.Response;

import java.time.LocalDateTime;

// 안 읽은 알림 조회(GET /notifications/unread) 응답 한 건.
public record CoupleNotificationDto(Long id, String type, String message, LocalDateTime createdAt) {
}
