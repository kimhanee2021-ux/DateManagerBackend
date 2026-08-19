package org.ict.datemanagerbackend.domain.couple.dto;

import java.time.LocalDateTime;

// 초대 생성(POST /invite) 응답: 발급된 토큰, 프론트가 바로 쓸 수 있는 전체 링크, 만료 시각
public record CoupleInviteResponse(String token, String inviteUrl, LocalDateTime expiresAt) {
}
