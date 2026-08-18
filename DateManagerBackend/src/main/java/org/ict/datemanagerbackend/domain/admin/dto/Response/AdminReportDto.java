package org.ict.datemanagerbackend.domain.admin.dto.Response;

import java.time.LocalDateTime;

public record AdminReportDto(Long id, Long reporterUserId, String reporterNickname, String targetType,
                              Long targetId, String reason, String status, LocalDateTime createdAt) {
}
