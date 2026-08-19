package org.ict.datemanagerbackend.domain.user.service;

import tools.jackson.databind.JsonNode;

// 토스페이먼츠 빌링(정기결제) API 연동. 시크릿 키로 Basic 인증을 걸어서 호출하며,
// 프론트에서 결제위젯으로 카드 등록 인증까지 마친 뒤 넘겨주는 authKey/customerKey로
// 빌링키를 발급받고, 이후 그 빌링키로 반복 결제를 승인 요청한다.
// 공식 문서: https://docs.tosspayments.com/guides/v2/billing/integration-api
public interface TossPaymentsService {

    // 카드 최초 등록 인증(프론트 결제위젯) 성공 후 받은 authKey+customerKey로 빌링키를 발급받는다.
    // 응답의 billingKey를 Subscription.billingKey에 저장해두면, 이후엔 카드 재인증 없이 정기 결제가 가능하다.
    JsonNode issueBillingKey(String authKey, String customerKey);

    // 발급된 빌링키로 실제 결제를 승인 요청한다. orderId는 매 결제마다 고유해야 한다(예: UUID).
    JsonNode charge(String billingKey, String customerKey, int amount, String orderId, String orderName);
}
