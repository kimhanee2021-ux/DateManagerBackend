package org.ict.datemanagerbackend.domain.user.dto;

// 빌링키 발급(POST /api/subscriptions/billing-key) 요청. authKey/customerKey는 프론트가
// 토스페이먼츠 결제위젯으로 카드 등록 인증을 마친 뒤 콜백으로 받는 값이다.
public record IssueBillingKeyRequest(String authKey, String customerKey, String planCode) {
}
