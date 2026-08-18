package org.ict.datemanagerbackend.domain.admin.dto.Response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCoupleDto(Long id, String status, LocalDateTime connectedAt, List<AdminCoupleMemberDto> members) {
}
