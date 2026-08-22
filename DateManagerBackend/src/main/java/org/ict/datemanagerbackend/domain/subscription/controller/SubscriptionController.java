package org.ict.datemanagerbackend.domain.subscription.controller;

import org.ict.datemanagerbackend.domain.subscription.dto.Request.IssueBillingKeyRequest;
import org.ict.datemanagerbackend.domain.subscription.dto.Response.SubscriptionDto;
import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.subscription.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;

// 구독(정기결제) API. 결제 흐름:
//   1) 프론트가 토스페이먼츠 결제위젯으로 카드 등록 인증을 마치면 authKey+customerKey를 받는다
//   2) POST /billing-key 로 그 값을 넘기면, 여기서 토스 API로 빌링키를 발급받아 Subscription에 저장하고
//      바로 첫 결제까지 진행한다 (가입 즉시 첫 달 요금 청구)
//   3) 이후 매일 새벽 SubscriptionRenewalScheduler가 만료일 지난 구독을 자동으로 재결제한다
//   4) 결제가 실패하면 status가 PAST_DUE로 바뀌는데, POST /charge로 수동 재시도할 수 있다
//   5) POST /cancel 로 구독을 해지하면 이후 자동 갱신 대상에서 제외된다
// 엔티티 조립/상태 전이 규칙은 전부 SubscriptionService에 있고, 컨트롤러는 요청 파싱과
// 서비스가 던진 예외를 HTTP 상태코드로 매핑하는 것만 담당한다.
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(UserRepository userRepository, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMySubscription(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        }
        Long userId = (Long) authentication.getPrincipal();
        return subscriptionService.findMySubscription(userId)
                .<ResponseEntity<?>>map(sub -> ResponseEntity.ok(toDto(sub)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("hasSubscription", false)));
    }

    @PostMapping("/billing-key")
    public ResponseEntity<?> issueBillingKey(Authentication authentication, @RequestBody IssueBillingKeyRequest req) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        }
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        if (req.authKey() == null || req.customerKey() == null || req.planCode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "authKey, customerKey, planCode가 모두 필요합니다"));
        }
        try {
            Subscription subscription = subscriptionService.registerBillingKeyAndCharge(
                    user, req.authKey(), req.customerKey(), req.planCode());
            return ResponseEntity.ok(toDto(subscription));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "토스페이먼츠 빌링키 발급에 실패했습니다"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    // PAST_DUE(결제 실패) 상태를 수동으로 재시도할 때 사용. 정상 구독 중엔 자동 갱신(스케줄러)만으로 충분하다.
    @PostMapping("/charge")
    public ResponseEntity<?> retryCharge(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        }
        Long userId = (Long) authentication.getPrincipal();
        try {
            Subscription subscription = subscriptionService.retryCharge(userId);
            return ResponseEntity.ok(toDto(subscription));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        }
        Long userId = (Long) authentication.getPrincipal();
        try {
            Subscription subscription = subscriptionService.cancel(userId);
            return ResponseEntity.ok(toDto(subscription));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private SubscriptionDto toDto(Subscription s) {
        return new SubscriptionDto(s.getId(), s.getPlanCode(), s.getStatus(), s.getStartedAt(),
                s.getExpiresAt(), s.getPaymentProvider(), s.getBillingKey() != null,
                s.getLastPaymentStatus(), s.getLastPaymentError());
    }
}
