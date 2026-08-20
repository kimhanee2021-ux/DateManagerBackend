package org.ict.datemanagerbackend.domain.subscription.service;

import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.util.Optional;

// 구독 결제(최초 등록 직후 첫 결제, 매일 자동 갱신, 수동 재시도)가 전부 chargeAndUpdate()를 거치도록
// 모아둔 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기 위해 분리함(2026-08-19).
public interface SubscriptionService {
  // <<유저의 가장 최근 구독 내역 조회>>
  Optional<Subscription> findMySubscription(Long userId);
  // <<빌링키 발급 + 가입 즉시 첫 결제>>
  Subscription registerBillingKeyAndCharge(User user, String authKey, String customerKey, String planCode);
  // <<PAST_DUE 상태 구독을 수동으로 재결제 시도>>
  Subscription retryCharge(Long userId);
  // <<구독 해지(만료일까지는 유지, 자동 갱신만 중단)>>
  Subscription cancel(Long userId);
  // <<결제 성공/실패에 따라 상태(status/expiresAt/lastPaymentStatus) 갱신>>
  Subscription chargeAndUpdate(Subscription subscription);
}
