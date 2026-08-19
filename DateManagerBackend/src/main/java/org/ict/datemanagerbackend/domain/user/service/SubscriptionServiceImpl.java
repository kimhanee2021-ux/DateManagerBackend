package org.ict.datemanagerbackend.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.user.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    // 테스트 단계 가격 - 프론트 SubscriptionModal.jsx의 PLAN.amount와 반드시 맞춰야 함
    private static final int MONTHLY_AMOUNT = 990;
    private static final String ORDER_NAME = "Date Manager 프리미엄 월간 구독";

    private final SubscriptionRepository subscriptionRepository;
    private final TossPaymentsService tossPaymentsService;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository, TossPaymentsService tossPaymentsService) {
        this.subscriptionRepository = subscriptionRepository;
        this.tossPaymentsService = tossPaymentsService;
    }

    @Override
    public Subscription chargeAndUpdate(Subscription subscription) {
        String orderId = UUID.randomUUID().toString();
        try {
            JsonNode result = tossPaymentsService.charge(
                    subscription.getBillingKey(), subscription.getCustomerKey(), MONTHLY_AMOUNT, orderId, ORDER_NAME);
            subscription.setStatus("ACTIVE");
            subscription.setLastPaymentStatus("SUCCESS");
            subscription.setLastPaymentError(null);
            subscription.setExpiresAt(LocalDateTime.now().plusMonths(1));
            log.info("구독 결제 성공: subscriptionId={}, orderId={}, status={}",
                    subscription.getId(), orderId, result.path("status").asText(null));
        } catch (HttpStatusCodeException e) {
            subscription.setStatus("PAST_DUE");
            subscription.setLastPaymentStatus("FAILED");
            subscription.setLastPaymentError(extractErrorMessage(e.getResponseBodyAsString()));
            log.error("구독 결제 실패: subscriptionId={}, orderId={}, {} {}",
                    subscription.getId(), orderId, e.getStatusCode(), e.getResponseBodyAsString());
        }
        return subscriptionRepository.save(subscription);
    }

    // 토스 에러 응답 {"code":"...","message":"..."} 에서 message만 뽑아 프론트에 그대로 보여줄 수 있게 한다.
    // 파싱 실패해도(형식이 다르면) 원본 문자열을 그대로 저장해서 최소한 원인 파악은 가능하게 둔다.
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null) return "알 수 없는 오류";
        int keyIdx = responseBody.indexOf("\"message\"");
        if (keyIdx < 0) {
            return responseBody.length() > 480 ? responseBody.substring(0, 480) : responseBody;
        }
        int colonIdx = responseBody.indexOf(':', keyIdx);
        int startIdx = responseBody.indexOf('"', colonIdx + 1) + 1;
        int endIdx = responseBody.indexOf('"', startIdx);
        if (startIdx > 0 && endIdx > startIdx) {
            return responseBody.substring(startIdx, endIdx);
        }
        return responseBody.length() > 480 ? responseBody.substring(0, 480) : responseBody;
    }
}
