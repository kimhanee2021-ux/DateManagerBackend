package org.ict.datemanagerbackend.domain.admin.dto.Response;

import java.time.LocalDateTime;

public record AdminUserDto(Long id, String email, String nickname, String gender, LocalDateTime createdAt, boolean subscribed) {
}
