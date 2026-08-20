package org.ict.datemanagerbackend.domain.admin.dto.Response;

public record AdminCoupleMemberDto(Long userId, String nickname, String email, String roleType, boolean subscribed) {
}
