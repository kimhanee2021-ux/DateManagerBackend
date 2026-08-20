package org.ict.datemanagerbackend.domain.subscription.service;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

// 토스페이먼츠 빌링(정기결제) API 연동. 시크릿 키로 Basic 인증을 걸어서 호출하며,
// 프론트에서 결제위젯으로 카드 등록 인증까지 마친 뒤 넘겨주는 authKey/customerKey로
// 빌링키를 발급받고, 이후 그 빌링키로 반복 결제를 승인 요청한다.
// 공식 문서: https://docs.tosspayments.com/guides/v2/billing/integration-api
@Service
@Slf4j
public class TossPaymentsServiceImpl implements TossPaymentsService {

    private static final String BASE_URL = "https://api.tosspayments.com/v1/billing";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${toss.secret-key}")
    private String secretKey;

    private HttpHeaders authHeaders() {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // 카드 최초 등록 인증(프론트 결제위젯) 성공 후 받은 authKey+customerKey로 빌링키를 발급받는다.
    // 응답의 billingKey를 Subscription.billingKey에 저장해두면, 이후엔 카드 재인증 없이 정기 결제가 가능하다.
    @Override
    public JsonNode issueBillingKey(String authKey, String customerKey) {
        String url = BASE_URL + "/authorizations/issue";
        Map<String, String> body = Map.of("authKey", authKey, "customerKey", customerKey);
        try {
            return restTemplate.postForObject(url, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            log.error("토스페이먼츠 빌링키 발급 실패: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    // 발급된 빌링키로 실제 결제를 승인 요청한다. orderId는 매 결제마다 고유해야 한다(예: UUID).
    @Override
    public JsonNode charge(String billingKey, String customerKey, int amount, String orderId, String orderName) {
        String url = BASE_URL + "/" + billingKey;
        Map<String, Object> body = Map.of(
                "customerKey", customerKey,
                "amount", amount,
                "orderId", orderId,
                "orderName", orderName
        );
        try {
            return restTemplate.postForObject(url, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            log.error("토스페이먼츠 정기결제 승인 실패: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}
