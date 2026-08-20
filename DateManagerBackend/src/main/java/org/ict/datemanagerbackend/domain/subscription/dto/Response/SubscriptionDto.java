package org.ict.datemanagerbackend.domain.subscription.dto.Response;

import java.time.LocalDateTime;

// 구독 조회/등록/재결제/해지 API가 공통으로 내려주는 응답. hasBillingKey만 boolean으로 노출하고
// 실제 billingKey 값 자체는 절대 프론트로 내려보내지 않는다(결제 수단 식별자라 민감정보).
public record SubscriptionDto(Long id, String planCode, String status, LocalDateTime startedAt,
                               LocalDateTime expiresAt, String paymentProvider, boolean hasBillingKey,
                               String lastPaymentStatus, String lastPaymentError) {
}
