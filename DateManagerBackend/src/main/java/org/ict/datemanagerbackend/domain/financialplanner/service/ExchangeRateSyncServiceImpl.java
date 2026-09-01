package org.ict.datemanagerbackend.domain.financialplanner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedExchangeRate;
import org.ict.datemanagerbackend.domain.financialplanner.repository.CachedExchangeRateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 한국수출입은행 환율 API 캐시 배치(2026-08-31). 호출 한도가 일일 1,000회로 넉넉하지 않아 요청마다
// 직접 호출하지 않고 1시간 주기로 전체 통화를 한 번에 받아 캐시한다(개발 명세서 3-3 보완사항).
// 주말/공휴일엔 그날 환율 자체가 고시되지 않아 응답이 빈 배열로 오므로, 최근 영업일을 찾을 때까지
// 최대 7일 전까지 거슬러 올라간다.
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateSyncServiceImpl implements ExchangeRateSyncService {

  private static final String URL = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final CachedExchangeRateRepository cachedExchangeRateRepository;
  private final RestTemplate restTemplate = buildRestTemplate();

  // koreaexim.go.kr가 리프 인증서만 보내고 중간 인증서(Thawte TLS RSA CA G1)를 안 보내서
  // (2026-08-31 openssl s_client로 실측 확인 - 브라우저는 알아서 보완하지만 JDK 기본 TLS는
  // 안 함) 기본 RestTemplate로는 SSLHandshakeException이 난다. JDK 기본 신뢰 앵커에 그
  // 누락된 중간 인증서 하나만 더해서 이 RestTemplate 전용 SSLContext를 만든다 - 다른 서비스의
  // HTTPS 호출엔 영향 없고(전역 JVM 신뢰저장소를 안 건드림), 신뢰를 추가만 하지 기존 신뢰를
  // 줄이진 않는다.
  private static RestTemplate buildRestTemplate() {
    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate intermediate;
      try (InputStream in = new ClassPathResource("certs/thawte-tls-rsa-ca-g1.pem").getInputStream()) {
        intermediate = cf.generateCertificate(in);
      }

      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      // JDK 기본 신뢰 앵커(cacerts)를 그대로 복사해온 뒤, 누락된 중간 인증서 하나만 추가한다.
      TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      defaultTmf.init((KeyStore) null);
      for (var tm : defaultTmf.getTrustManagers()) {
        if (tm instanceof javax.net.ssl.X509TrustManager x509tm) {
          int i = 0;
          for (var anchor : x509tm.getAcceptedIssuers()) {
            trustStore.setCertificateEntry("default-" + (i++), anchor);
          }
        }
      }
      trustStore.setCertificateEntry("thawte-tls-rsa-ca-g1", intermediate);

      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), null);
      SSLContext.setDefault(sslContext); // 이 SimpleClientHttpRequestFactory가 쓰는 HttpsURLConnection이 참조
      // koreaexim.go.kr가 세션 쿠키 없는 요청을 봇으로 보고 같은 URL로 리다이렉트를 반복시키는
      // 것으로 추정(2026-08-31 실측 - "Server redirected too many times") - 리다이렉트 사이에
      // 쿠키를 유지해야 통과할 수 있어 CookieManager를 기본으로 등록한다.
      if (CookieHandler.getDefault() == null) {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
      }

      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
          if (connection instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(sslContext.getSocketFactory());
          }
          super.prepareConnection(connection, httpMethod);
        }
      };
      return new RestTemplate(factory);
    } catch (Exception e) {
      // 실패해도 서비스 부팅 자체는 막지 않는다 - 기본 RestTemplate로 폴백(핸드셰이크 실패 시
      // syncRates()가 알아서 재시도 없이 조용히 넘어간다).
      log.warn("[ExchangeRateSyncService] 커스텀 TrustManager 구성 실패, 기본 RestTemplate로 폴백", e);
      return new RestTemplate();
    }
  }

  @Value("${exim.exchange.service-key}")
  private String serviceKey;

  @Scheduled(cron = "0 0 * * * *") // 매시 정각
  @Override
  public void syncRates() {
    for (int daysAgo = 0; daysAgo <= 7; daysAgo++) {
      String searchDate = LocalDate.now().minusDays(daysAgo).format(DATE_FMT);
      JsonNode root = fetchRates(searchDate);
      if (root == null) continue;
      if (!root.isArray() || root.isEmpty()) continue; // 휴장일 - 하루 더 전으로

      int upserted = 0;
      for (JsonNode item : root) {
        String currencyCode = normalizeCurrencyCode(item.path("cur_unit").asText(""));
        Double rate = parseRate(item.path("deal_bas_r").asText(null));
        if (currencyCode == null || rate == null) continue;

        CachedExchangeRate existing = cachedExchangeRateRepository.findById(currencyCode).orElse(null);
        Double prevRate = existing != null ? existing.getRate() : null;
        CachedExchangeRate rateEntity = existing != null ? existing
            : CachedExchangeRate.builder().currencyCode(currencyCode).build();
        rateEntity.setPrevRate(prevRate);
        rateEntity.setRate(rate);
        rateEntity.setFetchedAt(LocalDateTime.now());
        cachedExchangeRateRepository.save(rateEntity);
        upserted++;
      }
      log.info("[ExchangeRateSyncService] {} 기준 환율 {}건 반영 완료", searchDate, upserted);
      return;
    }
    log.warn("[ExchangeRateSyncService] 최근 7일 내 고시된 환율을 찾지 못함");
  }

  // "JPY(100)"처럼 100단위 표시가 붙는 통화(엔화 등)는 실제 통화코드만 남기고, 나중에 rate를 쓸 때
  // "100엔당 원화"라는 걸 감안해야 하지만 - 우리 서비스는 "환율이 오르는 추세인지" 브리핑용이라
  // 단위 자체보다 방향성이 중요해 여기선 통화코드만 정규화하고 100단위 환산은 하지 않는다.
  private String normalizeCurrencyCode(String curUnit) {
    if (curUnit == null || curUnit.isBlank()) return null;
    int paren = curUnit.indexOf('(');
    return (paren > 0 ? curUnit.substring(0, paren) : curUnit).trim();
  }

  private Double parseRate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.replace(",", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private JsonNode fetchRates(String searchDate) {
    String url = URL + "?authkey=" + serviceKey + "&searchdate=" + searchDate + "&data=AP01";
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
    headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
    headers.set(HttpHeaders.REFERER, "https://oapi.koreaexim.go.kr/");
    try {
      return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
    } catch (Exception e) {
      log.warn("[ExchangeRateSyncService] {} 조회 실패", searchDate, e);
      return null;
    }
  }
}
