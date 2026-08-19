package org.ict.datemanagerbackend.domain.user.service;

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
