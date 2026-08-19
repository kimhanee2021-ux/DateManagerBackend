package org.ict.datemanagerbackend.domain.user.dto;

// 마이페이지 프로필 수정(PUT /api/me) 요청. null/빈 문자열인 필드는 그대로 두고 값이 있는 필드만 바꾼다.
public record UpdateMeRequest(String nickname, String gender) {
}
