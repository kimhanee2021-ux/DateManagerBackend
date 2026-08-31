package org.ict.datemanagerbackend.domain.financialplanner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedFinancialProduct;
import org.ict.datemanagerbackend.domain.financialplanner.entity.ProductType;
import org.ict.datemanagerbackend.domain.financialplanner.repository.CachedFinancialProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// 금융감독원 finlife(금융상품통합비교공시) API에서 정기예금+적금 상품을 받아 캐시 테이블에
// upsert하는 배치. TourApiSyncServiceImpl과 동일하게 @Scheduled 새벽 배치 + 자체 RestTemplate
// 인스턴스 패턴을 따른다.
//
// 주의(2026-08-31 실측): 기본 User-Agent(RestTemplate 기본값/curl 기본값)로 호출하면 서버가 연결을
// 그냥 끊어버려서, 브라우저 User-Agent를 명시적으로 헤더에 실어야 응답이 온다.
@Service
@RequiredArgsConstructor
@Slf4j
public class FinlifeSyncServiceImpl implements FinlifeSyncService {

  private static final String BASE_URL = "https://finlife.fss.or.kr/finlifeapi";
  // 020000 = 은행(1금융권) - 스코프를 은행권으로 한정, 저축은행 등은 필요해지면 추가.
  private static final String TOP_FIN_GRP_NO = "020000";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

  private final CachedFinancialProductRepository cachedFinancialProductRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${fss.finlife.service-key}")
  private String serviceKey;

  @Scheduled(cron = "0 0 4 * * *")
  @Override
  public void syncProducts() {
    int upserted = 0;
    upserted += syncOne("depositProductsSearch", ProductType.DEPOSIT);
    upserted += syncOne("savingProductsSearch", ProductType.SAVING);
    log.info("[FinlifeSyncService] 예·적금 상품 옵션 {}건 반영 완료", upserted);
  }

  private int syncOne(String endpoint, ProductType productType) {
    int count = 0;
    int pageNo = 1;
    while (true) {
      JsonNode root = fetchPage(endpoint, pageNo);
      if (root == null) break;
      JsonNode result = root.path("result");
      if (!"000".equals(result.path("err_cd").asText())) {
        log.warn("[FinlifeSyncService] {} 응답 오류: {}", endpoint, result.path("err_msg").asText());
        break;
      }

      // baseList(상품 기본정보)를 (fin_co_no, fin_prdt_cd) 키로 먼저 모아두고, optionList(기간별
      // 금리)를 돌면서 은행명/상품명을 붙인다 - finlife 응답이 두 리스트로 나뉘어 와서 조인이 필요.
      Map<String, String[]> baseByKey = new HashMap<>(); // key -> [bankName, productName]
      for (JsonNode base : result.path("baseList")) {
        String key = base.path("fin_co_no").asText() + "|" + base.path("fin_prdt_cd").asText();
        baseByKey.put(key, new String[]{base.path("kor_co_nm").asText(), base.path("fin_prdt_nm").asText()});
      }

      for (JsonNode option : result.path("optionList")) {
        String finCoNo = option.path("fin_co_no").asText();
        String finPrdtCd = option.path("fin_prdt_cd").asText();
        String key = finCoNo + "|" + finPrdtCd;
        String[] base = baseByKey.get(key);
        if (base == null) continue;

        Integer saveTrm = option.path("save_trm").asInt(-1);
        if (saveTrm <= 0) continue;

        CachedFinancialProduct product = cachedFinancialProductRepository
            .findByFinCoNoAndFinPrdtCdAndSaveTrmAndProductType(finCoNo, finPrdtCd, saveTrm, productType)
            .orElseGet(() -> CachedFinancialProduct.builder()
                .finCoNo(finCoNo).finPrdtCd(finPrdtCd).saveTrm(saveTrm).productType(productType)
                .build());
        product.setBankName(base[0]);
        product.setProductName(base[1]);
        product.setIntrRate(option.path("intr_rate").isNumber() ? option.path("intr_rate").asDouble() : null);
        product.setIntrRate2(option.path("intr_rate2").isNumber() ? option.path("intr_rate2").asDouble() : null);
        product.setSyncedAt(LocalDateTime.now());
        cachedFinancialProductRepository.save(product);
        count++;
      }

      int maxPageNo = result.path("max_page_no").asInt(1);
      if (pageNo >= maxPageNo) break;
      pageNo++;
    }
    return count;
  }

  private JsonNode fetchPage(String endpoint, int pageNo) {
    String url = UriComponentsBuilder.fromUriString(BASE_URL + "/" + endpoint + ".json")
        .queryParam("auth", serviceKey)
        .queryParam("topFinGrpNo", TOP_FIN_GRP_NO)
        .queryParam("pageNo", pageNo)
        .build(false)
        .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
    try {
      return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
    } catch (Exception e) {
      log.warn("[FinlifeSyncService] {} 호출 실패(pageNo={})", endpoint, pageNo, e);
      return null;
    }
  }
}
