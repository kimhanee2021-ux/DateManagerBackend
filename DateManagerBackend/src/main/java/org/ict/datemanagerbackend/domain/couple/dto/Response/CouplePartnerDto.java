package org.ict.datemanagerbackend.domain.couple.dto.Response;

// 상대방 정보. profileImageUrl이 있으면 프론트가 그대로 쓰고, 없으면 gender 기준 기본 이미지로
// 대체한다(프론트 Avatar 컴포넌트 참고 - 업로드 사진 우선, 없으면 성별 기본 이미지).
public record CouplePartnerDto(Long userId, String nickname, String email, String gender, String profileImageUrl) {
}
