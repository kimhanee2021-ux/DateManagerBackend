package org.ict.datemanagerbackend.domain.subscription.service;

import tools.jackson.databind.JsonNode;

// 토스페이먼츠 빌링(정기결제) API 연동 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과
// 통일하기 위해 분리함(2026-08-19). 실제 연동 로직은 TossPaymentsServiceImpl 참고.
public interface TossPaymentsService {
  // <<카드 최초 등록 인증 성공 후 authKey+customerKey로 빌링키 발급>>
  JsonNode issueBillingKey(String authKey, String customerKey);
  // <<발급된 빌링키로 실제 결제 승인 요청>>
  JsonNode charge(String billingKey, String customerKey, int amount, String orderId, String orderName);
}
