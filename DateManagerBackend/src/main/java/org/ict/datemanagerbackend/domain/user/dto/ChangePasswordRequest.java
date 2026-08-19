package org.ict.datemanagerbackend.domain.user.dto;

// 비밀번호 변경(PUT /api/me/password) 요청. 소셜 로그인 전용 계정(passwordHash 없음)은
// currentPassword를 검증하지 않으므로 null로 보내도 된다.
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
