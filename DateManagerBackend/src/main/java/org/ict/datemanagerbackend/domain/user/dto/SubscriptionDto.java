package org.ict.datemanagerbackend.domain.user.dto;

import org.ict.datemanagerbackend.domain.user.entity.Subscription;

import java.time.LocalDateTime;

// 내 구독 조회/발급/재결제/해지 응답 DTO
public record SubscriptionDto(Long id, String planCode, String status, LocalDateTime startedAt,
                               LocalDateTime expiresAt, String paymentProvider, boolean hasBillingKey,
                               String lastPaymentStatus, String lastPaymentError) {

  public static SubscriptionDto from(Subscription s) {
    return new SubscriptionDto(s.getId(), s.getPlanCode(), s.getStatus(), s.getStartedAt(),
        s.getExpiresAt(), s.getPaymentProvider(), s.getBillingKey() != null,
        s.getLastPaymentStatus(), s.getLastPaymentError());
  }
}
