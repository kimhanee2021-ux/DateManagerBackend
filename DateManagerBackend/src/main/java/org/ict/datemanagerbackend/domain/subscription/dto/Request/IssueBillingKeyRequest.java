package org.ict.datemanagerbackend.domain.subscription.dto.Request;

// 카드 최초 등록 인증(프론트 결제위젯) 완료 후 넘어오는 authKey+customerKey로 빌링키를
// 발급받아 첫 결제까지 진행할 때 쓰는 요청 본문.
public record IssueBillingKeyRequest(String authKey, String customerKey, String planCode) {
}
