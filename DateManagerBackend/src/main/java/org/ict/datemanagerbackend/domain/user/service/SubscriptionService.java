package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.entity.Subscription;

// 구독 결제(최초 등록 직후 첫 결제, 매일 자동 갱신, 수동 재시도)가 전부 이 서비스의
// chargeAndUpdate()를 거치도록 모아뒀다 - 결제 성공/실패에 따른 상태(status/expiresAt/
// lastPaymentStatus) 갱신 로직을 한 곳에서만 관리하기 위함.
public interface SubscriptionService {

    // 결제 성공하면 ACTIVE + 다음 만료일(1개월 뒤)로 갱신, 실패하면 PAST_DUE로 바꾸고
    // 실패 사유를 저장한다. 어느 쪽이든 항상 저장까지 마치고 리턴한다(예외를 던지지 않음).
    Subscription chargeAndUpdate(Subscription subscription);
}
