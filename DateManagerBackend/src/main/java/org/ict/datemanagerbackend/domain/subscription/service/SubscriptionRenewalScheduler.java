package org.ict.datemanagerbackend.domain.subscription.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 매일 새벽, 만료일이 지난 ACTIVE 구독을 찾아서 빌링키로 자동 재결제한다.
// PAST_DUE(결제 실패)나 CANCELED 상태는 대상에서 제외 - 실패한 건은 사용자가 카드를 바꾸는 등
// 직접 조치해야 하므로 매일 자동으로 재시도하지 않는다(무한 실패 로그 방지).
@Component
@Slf4j
public class SubscriptionRenewalScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionRenewalScheduler(SubscriptionRepository subscriptionRepository, SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void renewDueSubscriptions() {
        List<Subscription> due = subscriptionRepository.findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
        if (due.isEmpty()) return;
        log.info("구독 자동 갱신 대상 {}건 처리 시작", due.size());
        for (Subscription subscription : due) {
            subscriptionService.chargeAndUpdate(subscription);
        }
    }
}
