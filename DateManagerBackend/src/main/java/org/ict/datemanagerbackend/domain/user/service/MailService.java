package org.ict.datemanagerbackend.domain.user.service;

// 이메일 발송을 담당한다. 지금은 비밀번호 재설정 메일 하나뿐이지만, 나중에 다른 알림 메일이 생겨도
// 발송 로직 자체는 여기로 모아두고 메서드만 추가하면 되도록 분리해뒀다.
public interface MailService {

    // resetLink는 프론트 재설정 화면 URL(토큰 쿼리 파라미터 포함) - AuthController가 조립해서 넘겨준다.
    void sendPasswordResetEmail(String to, String resetLink);
}
