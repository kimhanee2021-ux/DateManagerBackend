package org.ict.datemanagerbackend.domain.user.controller;

import tools.jackson.databind.JsonNode;
import org.ict.datemanagerbackend.domain.user.dto.IssueBillingKeyRequest;
import org.ict.datemanagerbackend.domain.user.dto.SubscriptionDto;
import org.ict.datemanagerbackend.domain.user.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.SubscriptionRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.user.service.SubscriptionService;
import org.ict.datemanagerbackend.domain.user.service.TossPaymentsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;
import java.util.Optional;

// 구독(정기결제) API. 결제 흐름:
//   1) 프론트가 토스페이먼츠 결제위젯으로 카드 등록 인증을 마치면 authKey+customerKey를 받는다
//   2) POST /billing-key 로 그 값을 넘기면, 여기서 토스 API로 빌링키를 발급받아 Subscription에 저장하고
//      바로 첫 결제까지 진행한다 (가입 즉시 첫 달 요금 청구)
//   3) 이후 매일 새벽 SubscriptionRenewalScheduler가 만료일 지난 구독을 자동으로 재결제한다
//   4) 결제가 실패하면 status가 PAST_DUE로 바뀌는데, POST /charge로 수동 재시도할 수 있다
//   5) POST /cancel 로 구독을 해지하면 이후 자동 갱신 대상에서 제외된다
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TossPaymentsService tossPaymentsService;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                                   TossPaymentsService tossPaymentsService, SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.tossPaymentsService = tossPaymentsService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMySubscription(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Optional<Subscription> sub = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if (sub.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasSubscription", false));
        }
        return ResponseEntity.ok(SubscriptionDto.from(sub.get()));
    }

    @PostMapping("/billing-key")
    public ResponseEntity<?> issueBillingKey(Authentication authentication, @RequestBody IssueBillingKeyRequest req) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        if (req.authKey() == null || req.customerKey() == null || req.planCode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "authKey, customerKey, planCode가 모두 필요합니다"));
        }
        JsonNode result;
        try {
            result = tossPaymentsService.issueBillingKey(req.authKey(), req.customerKey());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "토스페이먼츠 빌링키 발급에 실패했습니다"));
        }
        String billingKey = result.path("billingKey").asText(null);
        if (billingKey == null) {
            return ResponseEntity.status(502).body(Map.of("error", "빌링키를 받지 못했습니다"));
        }
        Subscription subscription = Subscription.builder()
                .user(user)
                .planCode(req.planCode())
                .paymentProvider("TOSS")
                .billingKey(billingKey)
                .customerKey(req.customerKey())
                .build();
        subscription = subscriptionService.chargeAndUpdate(subscription); // 가입 즉시 첫 결제
        return ResponseEntity.ok(SubscriptionDto.from(subscription));
    }

    // PAST_DUE(결제 실패) 상태를 수동으로 재시도할 때 사용. 정상 구독 중엔 자동 갱신(스케줄러)만으로 충분하다.
    @PostMapping("/charge")
    public ResponseEntity<?> retryCharge(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Subscription subscription = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (subscription == null || subscription.getBillingKey() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "등록된 빌링키가 없습니다. 먼저 카드를 등록해주세요"));
        }
        if ("CANCELED".equals(subscription.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 해지된 구독입니다"));
        }
        subscription = subscriptionService.chargeAndUpdate(subscription);
        return ResponseEntity.ok(SubscriptionDto.from(subscription));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Subscription subscription = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (subscription == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "구독 내역이 없습니다"));
        }
        if ("CANCELED".equals(subscription.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 해지된 구독입니다"));
        }
        // 만료일(expiresAt)은 그대로 둔다 - 이미 낸 이용 기간까지는 유지되고, 그 이후 자동 갱신만 안 되는 방식
        subscription.setStatus("CANCELED");
        subscriptionRepository.save(subscription);
        return ResponseEntity.ok(SubscriptionDto.from(subscription));
    }
}
